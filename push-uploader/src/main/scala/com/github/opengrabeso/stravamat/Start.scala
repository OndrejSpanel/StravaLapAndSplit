package com.github.opengrabeso.stravamat

import java.awt.Desktop
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, coding}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, BindFailedException}

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, duration}
import scala.util.{Success, Try}
import scala.xml.{Elem, XML}
import org.joda.time.{DateTime => ZonedDateTime}
import Util._

import scala.util.control.NonFatal

object Start extends App {

  case class AuthData(userId: String, since: ZonedDateTime)

  private val instanceId = System.currentTimeMillis()
  private var authData = Option.empty[AuthData]
  private val authDone = new CountDownLatch(1)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

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

  private val serverPort = 8088 // do not use 8080, would conflict with Google App Engine Dev Server

  private case class ServerInfo(system: ActorSystem, binding: Future[ServerBinding]) {
    def stop(): Unit = {
      implicit val executionContext = system.dispatcher
      binding.flatMap(_.unbind()) // trigger unbinding from the port
    }

    def shutdown(): Unit = {
      implicit val executionContext = system.dispatcher
      binding
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done

    }

  }

  private def shutdownAnotherInstance() = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val localServerUrl = s"http://localhost:$serverPort/shutdown?id=$instanceId"

    val localRequest = Http().singleRequest(HttpRequest(uri = localServerUrl)).map(_.discardEntityBytes())

    // try communicating with the local Stravamat, if not responding, use the remote one
    Try(Await.result(localRequest, Duration(2000, duration.MILLISECONDS)))
  }

  private val stravamatLocalUrl = "http://localhost:8080"
  private val stravamatRemoteUrl = "https://stravamat.appspot.com"
  private var useLocal = false
  private lazy val stravaMatUrl = if (useLocal) stravamatLocalUrl else stravamatRemoteUrl

  private def checkLocalStravamat(): Boolean = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val localTest = true

    val localRequest = Http().singleRequest(HttpRequest(uri = stravamatLocalUrl + "/ping")).map(_.discardEntityBytes())

    // try communicating with the local Stravamat, if not responding, use the remote one
    useLocal = localTest && Try(Await.result(localRequest, Duration(2000, duration.MILLISECONDS))).isSuccess
    useLocal
  }

  private def startBrowser() = {
    /*
    Authentication dance
    - request Stravamat to perform authentication, including user selection
     - http://stravamat/push-start?port=<XXXX>
    - Stravamat knowns or gets the Strava auth token (user id hash)
    - it generates a Stravamat token and sends it back by calling http://localhost:<XXXX>/auth?token=<ttttttttttt>
     - this is captured by authHandler
    - we receive the token and redirect to a page http://stravamat/push-push?token=<XXXX>
    */

    val startPushUrl = s"$stravaMatUrl/push-start?port=$serverPort"
    println(s"Starting browser $startPushUrl")
    Desktop.getDesktop.browse(new URL(startPushUrl).toURI)
  }


  def authHandler(userId: String, since: String) = {
    // session is authorized, we can continue sending the data
    serverInfo.stop()
    println(s"Auth done - Stravamat user id $userId")
    val sinceTime = new ZonedDateTime(since)
    val sinceTime2 = ZonedDateTime.parse(since)
    authData = Some(AuthData(userId, sinceTime))
    authDone.countDown()
    val doPushUrl = s"$stravaMatUrl/push-do"
    redirect(doPushUrl, StatusCodes.Found)
  }

  def shutdownHandler(id: Long): HttpResponse = {
    val response = if (id !=instanceId) {
      println(s"Shutdown - stop server $instanceId, received $id")
      serverInfo.shutdown()
      <result>Done</result>
    } else {
      println(s"Shutdown ignored - same instance")
      <result>Ignored - same instance</result>
    }

    sendResponseXml(200, response)
  }

  private def startHttpServer(callbackPort: Int): ServerInfo = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val requests = path("auth") {
      parameters('user, 'since) { (user, since) =>
        authHandler(user, since)
      }
    } ~ path("shutdown") {
      parameter('id) {
        id =>
          complete(shutdownHandler(id.toLong))
      }
    }

    val route = get(requests)

    def startIt() = {
      Http().bindAndHandle(Route.handlerFlow(route), "localhost", callbackPort)
    }

    val bindingFuture: Future[ServerBinding] = startIt().recoverWith {
      case _: BindFailedException =>
        // an old hanging instance of this app may be already running, request it to shut down
        shutdownAnotherInstance()
        startIt()
    }

    println(s"Auth server $instanceId started, listening on http://localhost:$callbackPort")
    // TODO: we may never receive oauth answer, the session may be terminated or there may be an error
    // we should time-out gracefully in such case, as not to block the server port
    // currently we at least let the new server shut down us so that instances are not multiplying
    ServerInfo(system, bindingFuture)
  }

  private def waitForServerToStop(serverInfo: ServerInfo) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    try {
      val binding = Await.result(serverInfo.binding, Duration.Inf)
      authDone.await()

    } catch {
      case NonFatal(_) =>
        println("Server not started")
        serverInfo.system.terminate()
    }
  }

  checkLocalStravamat()
  private val serverInfo = startHttpServer(serverPort)

  def performUpload(data: AuthData) = {
    val AuthData(userId, since) = data

    val sinceDate = since minusDays 1

    import scala.concurrent.ExecutionContext.Implicits.global

    // TODO: push-put-check is just an example, replace with real file processing

    val listFiles = MoveslinkFiles.listFiles.toList
    // sort files by timestamp
    val wantedFiles = listFiles.filter(MoveslinkFiles.timestampFromName(_).forall(_ > sinceDate))

    val sortedFiles = wantedFiles.sortBy(MoveslinkFiles.timestampFromName)

    val timeout = 30.seconds
    for {
      f <- sortedFiles
      fileBytes <- MoveslinkFiles.get(f)
    } {


      val digest = Digest.digest(fileBytes)

      // check by putting a digest first
      val uri = s"$stravaMatUrl/push-put-digest?user=$userId&path=$f"
      val req = Http().singleRequest(
        HttpRequest(
          uri = uri,
          method = HttpMethods.POST,
          entity = HttpEntity(digest)
        )
      ).map { resp =>
        resp.discardEntityBytes()
        resp.status
      }
      val resp = Await.result(req, Duration.Inf)

      println(s"File $f status $resp")
    }

    serverInfo.system.terminate()

  }


  startBrowser()


  waitForServerToStop(serverInfo)

  // server is stopped once auth information is received into authUserId, or when another instance has forced a shutdown
  for (data <- authData) {
    performUpload(data)
  }

  Await.result(serverInfo.system.whenTerminated, Duration.Inf)
  println("System stopped")

}
