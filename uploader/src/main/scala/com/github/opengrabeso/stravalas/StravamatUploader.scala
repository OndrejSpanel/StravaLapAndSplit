package com.github.opengrabeso.stravalas

import java.security.MessageDigest

import org.joda.time.{DateTime => ZonedDateTime}
import Util._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl._
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Success
import scala.xml.Elem

object StravamatUploader extends App {

  private val enumPath = "enum"
  private val donePath = "done"
  private val getPath = "get"
  private val hashPath = "digest"

  object HttpHandlerHelper {

    implicit class Prefixed(responseXml: Elem) {
      def prefixed: String = {
        val prefix = """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
        prefix + responseXml.toString
      }
    }

    private def sendResponseWithContentType(code: Int, response: String, ct: WithCharset) = {
      HttpResponse(status = code, entity = HttpEntity(ct, response))
    }

    def sendResponseHtml(code: Int, response: Elem): HttpResponse = {
      sendResponseWithContentType(code, response.toString, ContentTypes.`text/html(UTF-8)`)
    }

    def sendResponseXml(code: Int, responseXml: Elem): HttpResponse = {
      sendResponseWithContentType(code, responseXml.prefixed, ContentTypes.`text/xml(UTF-8)`)
    }
    def sendResponseBytes(code: Int, response: Array[Byte]): HttpResponse = {
      val ct = ContentTypes.`application/octet-stream`
      HttpResponse(status = code, entity = HttpEntity(ct, response))
    }
  }

  import HttpHandlerHelper._

  def enumHandler(since: Option[String]): HttpResponse = {
    println("enum")


    val sinceDate = since.map { s =>
      val v = ZonedDateTime.parse(s)
      v.minusDays(1) // timezone may be wrong, to be sure we are not skipping too much, move one day behind
    }


    def timestampFromName(name: String): Option[ZonedDateTime] = {
      // extract timestamp
      // GPS filename: Moveslink2/34FB984612000700-2017-05-23T16_27_11-0.sml
      val gpsPattern = "\\/.*-(\\d*)-(\\d*)-(\\d*)T(\\d*)_(\\d*)_(\\d*)-".r.unanchored
      // Quest filename Moveslink/Quest_2596420792_20170510143253.xml
      val questPattern = "\\/Quest_\\d*_(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)\\.".r.unanchored
      // note: may be different timezones, but a rough sort in enough for us (date is important)
      name match {
        case gpsPattern(yyyy,mm,dd,h,m,s) =>
          Some(ZonedDateTime.parse(s"$yyyy-$mm-${dd}T$h:$m:$s")) // TODO: DRY
        case questPattern(yyyy,mm,dd,h,m,s) =>
          Some(ZonedDateTime.parse(s"$yyyy-$mm-${dd}T$h:$m:$s")) // TODO: DRY
        case _ =>
          None
      }
    }

    val listFiles = MoveslinkFiles.listFiles.toList
    // sort files by timestamp
    val wantedFiles = sinceDate.fold(listFiles)(since => listFiles.filter(timestampFromName(_).forall(_ > since)))

    val sortedFiles = wantedFiles.sortBy(timestampFromName)

    val response = <files>
      {sortedFiles.map { file =>
        <file>{file}</file>
      }}
    </files>
    sendResponseXml(200, response)
  }


  val serverInfo = startHttpServer(8088) // do not use 8080, would conflict with Google App Engine Dev Server

  case class FileInfo(name: String, content: Option[Array[Byte]])

  // typically the same file is checked for digest and then read - avoid reading it twice
  var lastFile = Option.empty[FileInfo]

  def getCached(name: String): Option[Array[Byte]] = {
    lastFile.filter(_.name == name).fold {
      val ret = MoveslinkFiles.get(name)
      lastFile = Some(FileInfo(name, ret))
      ret
    }(_.content)
  }


  def getHandler(path: String): HttpResponse = {
    println(s"Get path $path")
    getCached(path).fold {
      sendResponseBytes(404, Array())
    } { f =>
      // send binary response
      sendResponseBytes(200, f)
    }
  }

  // TODO: DRY with Main.digest
  private val md = MessageDigest.getInstance("SHA-256")

  def digest(bytes: Array[Byte]): String = {
    val digestBytes = (0:Byte) +: md.digest(bytes) // prepend 0 byte to avoid negative sign
    BigInt(digestBytes).toString(16)
  }

  def digest(str: String): String = digest(str.getBytes)

  def digestHandler(path: String): HttpResponse = {
    println(s"Get digest $path")
    getCached(path).fold {
      val response = <error>
        <message>No such file</message>
        <filename> {path} </filename>
      </error>
      sendResponseXml(404, response)
    } { f =>
      val response = <digest>
        <message>File found</message>
        <filename>{path}</filename>
        <value>{digest(f)}</value>
      </digest>
      sendResponseXml(200, response)
    }
  }

  def optionsHandler(): HttpResponse = {
    val response = <result>OK</result>
    val ret = sendResponseXml(200, response)
    ret
  }

  def doneHandler(): HttpResponse = {
    val response = <result>Done</result>
    val ret = sendResponseXml(200, response)
    // once the message goes out, we can stop the server
    println("Done - stop server")
    serverInfo.stop()
    ret
  }

  case class ServerInfo(system: ActorSystem, binding: Future[ServerBinding]) {
    def stop(): Unit = {
      implicit val executionContext = system.dispatcher
      binding
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done
    }

  }


  object CorsSupport {
    //HttpOriginRange.*
    lazy val allowedOrigin = HttpOriginRange(
      HttpOrigin("http://localhost:8080"),
      HttpOrigin("http://stravamat.appspot.com")
      // it is no use allowing https://stravamat.appspot.com, as it cannot access plain unsecure http anyway (mixed content)
    )

    //this directive adds access control headers to normal responses
    def accessControl(origins: Seq[HttpOrigin]): List[HttpHeader] = {
      origins.find(allowedOrigin.matches).toList.flatMap { origin =>
        List(
          `Access-Control-Allow-Origin`(origin),
          `Access-Control-Allow-Headers`("Content-Type", "X-Requested-With"),
          `Access-Control-Max-Age`(60)
        )
      }
    }
  }

  private def startHttpServer(callbackPort: Int): ServerInfo = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val requests = path(enumPath) {
      parameters('since.?) { since =>
        encodeResponseWith(coding.Gzip, coding.NoCoding) {complete(enumHandler(since))}
      }
    } ~ path(getPath) {
      parameters('path) { path =>
        encodeResponseWith(coding.Gzip, coding.NoCoding) {complete(getHandler(path))}
      }
    } ~ path(hashPath) {
      parameters('path) { path =>
        complete(digestHandler(path))
      }
    } ~ path(donePath) {
      complete(doneHandler())
    }

    val route = post {

      checkSameOrigin(CorsSupport.allowedOrigin) {
        val originHeader = headerValueByType[Origin](())
        originHeader { origin =>
          respondWithHeaders(CorsSupport.accessControl(origin.origins))(requests)
        }

      }
    } ~ get(requests) ~ options {
      checkSameOrigin(CorsSupport.allowedOrigin) {
        val originHeader = headerValueByType[Origin](())
        originHeader { origin =>
          respondWithHeaders(CorsSupport.accessControl(origin.origins))(complete(optionsHandler()))
        }

      }

    }

    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(Route.handlerFlow(route), "localhost", callbackPort)

    println(s"Server started, listening on http://localhost:$callbackPort")
    println(s"  http://localhost:$callbackPort/enum")
    println(s"  http://localhost:$callbackPort/done")
    ServerInfo(system, bindingFuture)
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  serverInfo.binding.onComplete {
    case Success(_) =>
      // wait until the server has stopped
      Await.result(serverInfo.system.whenTerminated, Duration.Inf)
      println("Server stopped")
    case _ =>
      println("Server not started")
      serverInfo.system.terminate()
  }



}
