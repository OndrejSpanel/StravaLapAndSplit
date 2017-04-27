package com.github.opengrabeso.stravalas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route



import akka.stream.ActorMaterializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.xml.Elem

object StravamatUploader extends App {

  private val enumPath = "enum"
  private val donePath = "done"
  private val getPath = "get"

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

      <file></file>
    </files>
    sendResponseXml(200, response)
  }


  val serverInfo = startHttpServer(8088) // do not use 8080, would conflict with Google App Engine Dev Server

  def getHandler(path: String): HttpResponse = {
    println(s"Get path $path")
    val response = <error>
      <message>No such file</message>
      <file>path</file>
    </error>
    sendResponseXml(400, response)
  }
  def doneHandler(): HttpResponse = {
    val response = <html>Done</html>
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
    lazy val allowedOrigin = {
      HttpOrigin("*") // TODO: restrict
    }

    //this directive adds access control headers to normal responses
    private def addAccessControlHeaders = {
      mapResponseHeaders { headers =>
        //`Access-Control-Allow-Origin`(allowedOrigin) +:
        `Access-Control-Allow-Origin`.* +:
        `Access-Control-Allow-Credentials`(true) +:
        `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With") +:
        headers
      }
    }


    // from https://groups.google.com/forum/#!topic/akka-user/5RCZIJt7jHo
    //this handles preflight OPTIONS requests.
    //otherwise has to be under addAccessControlHeaders
    private def preflightRequestHandler: Route = options {
      complete(HttpResponse(200).withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
    }

    def apply(r: Route) = addAccessControlHeaders {
      preflightRequestHandler ~ r
    }
  }



  private def startHttpServer(callbackPort: Int): ServerInfo = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val route = get {
      path(enumPath) {
        complete(enumHandler())
      } ~ path(getPath) {
        parameters('path) { path =>
          complete(getHandler(path))
        }
      } ~ path(donePath) {
        complete(doneHandler())
      }
    }

    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(Route.handlerFlow(CorsSupport(route)), "localhost", callbackPort)

    println(s"Server started, listening on http://localhost:$callbackPort")
    println(s"  http://localhost:$callbackPort/enum")
    println(s"  http://localhost:$callbackPort/done")
    ServerInfo(system, bindingFuture)
  }

  // wait until the server has stopped
  Await.result(serverInfo.system.whenTerminated, Duration.Inf)
  println("Server stopped")


}
