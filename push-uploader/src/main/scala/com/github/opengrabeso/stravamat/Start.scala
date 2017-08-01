package com.github.opengrabeso.stravamat

import java.awt.Desktop
import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, coding}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, BindFailedException}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, duration}
import scala.util.control.NonFatal
import scala.util.{Success, Try}
import scala.xml.Elem

object Start extends App {

  private val instanceId = System.currentTimeMillis()

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
  private val stravamatRemoteUrl = "https://stravamat.appspot.com/"

  private def checkLocalStravamat(): Boolean = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val localTest = true

    val localRequest = Http().singleRequest(HttpRequest(uri = stravamatLocalUrl + "/ping")).map(_.discardEntityBytes())

    // try communicating with the local Stravamat, if not responding, use the remote one
    val useLocal = localTest && Try(Await.result(localRequest, Duration(2000, duration.MILLISECONDS))).isSuccess
    useLocal
  }

  private def startBrowser(local: Boolean) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val useLocal = checkLocalStravamat()

    val stravaMatUrl = if (useLocal) stravamatLocalUrl else stravamatRemoteUrl

    // open browser, ask Stravamat to perform OAuth dance
    val startPushUrl = s"$stravaMatUrl/push&port=$serverPort"
    Desktop.getDesktop.browse(new URL(startPushUrl).toURI)
  }


  def authHandler(ok: Boolean): HttpResponse = {
    // session is authorized, we can continue sending the data
    val response = <result>Done</result>
    val ret = sendResponseXml(200, response)
    println("Done - stop server")
    serverInfo.stop()
    ret
  }

  def shutdownHandler(id: Long): HttpResponse = {
    println(s"Shutdown - stop server $instanceId, received $id")
    val response = if (id !=instanceId) {
      serverInfo.stop()
      <result>Done</result>
    } else {
      <result>Ignored - same instance</result>
    }

    sendResponseXml(200, response)
  }

  private def startHttpServer(callbackPort: Int): ServerInfo = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val requests = path("auth") {
      parameter('status) { status =>
        complete(authHandler(status == "ok"))
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

  private val isLocalStravamat = checkLocalStravamat()
  private val serverInfo = startHttpServer(serverPort)

  startBrowser(isLocalStravamat)

  waitForServerToStop(serverInfo)


}
