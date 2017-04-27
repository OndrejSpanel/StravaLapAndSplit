package com.github.opengrabeso.stravalas

import java.security.MessageDigest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
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
  }

  import HttpHandlerHelper._

  def enumHandler(): HttpResponse = {
    println("enum")
    val response = <files>
      {MoveslinkFiles.listFiles.map { file =>
        <file>{file}</file>
      }}
    </files>
    sendResponseXml(200, response)
  }


  val serverInfo = startHttpServer(8088) // do not use 8080, would conflict with Google App Engine Dev Server

  case class FileInfo(name: String, content: Option[String])

  // typically the same file is checked for digest and then read - avoid reading it twice
  var lastFile = Option.empty[FileInfo]

  def getCached(name: String): Option[String] = {
    lastFile.filter(_.name == name).fold {
      val ret = MoveslinkFiles.get(name)
      lastFile = Some(FileInfo(name, ret))
      ret
    }(_.content)
  }


  def getHandler(path: String): HttpResponse = {
    println(s"Get path $path")
    getCached(path).fold {
      val response = <error>
        <message>No such file</message>
        <filename> {path} </filename>
      </error>
      sendResponseXml(404, response)
    } { f =>
      val response = <file>
        <message>File found</message>
        <filename> {path} </filename>
        <content> {f} </content>
      </file>
      sendResponseXml(200, response)
    }
  }

  // TODO: DRY with Main.digest
  private val md = MessageDigest.getInstance("SHA-256")

  def digest(str: String): String = {
    val digestBytes = (0:Byte) +: md.digest(str.getBytes) // prepend 0 byte to avoid negative sign
    BigInt(digestBytes).toString(16)
  }


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
      complete(enumHandler())
    } ~ path(getPath) {
      parameters('path) { path =>
        complete(getHandler(path))
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
    } ~ get(requests)

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
