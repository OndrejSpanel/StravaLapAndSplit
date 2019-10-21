package com.github.opengrabeso.mixtio

import java.io.{File, FileInputStream}
import org.apache.commons.io.IOUtils

import spark.{Request, Response, Route}
import spark.servlet.SparkApplication
import spark.Spark._

object ServletRouting {
  import requests._

  def route(path: String)(handleFunc: (Request, Response) => AnyRef): Route = {
    new Route(path) {
      override def handle(request: Request, response: Response) = handleFunc(request, response)
    }
  }

  val handlers: Seq[DefineRequest] = Seq(
    IndexHtml,

    FrontendStyle,
    FrontendScript, UdashApp,

    push.Ping, push.PushStart,

    Cleanup
  )

  def init() {
    // add any type derived from DefineRequest here
    // solution with reflection is elegant, but overcomplicated (and hard to get working with Google App Engine) and slow
    def addPage(h: DefineRequest) = {
      val r = route(h.handleUri) (h.apply)
      h.method match {
        case Method.Get => get(r)
        case Method.Put => put(r)
        case Method.Post => post(r)
        case Method.Delete => delete(r)
      }
    }

    handlers.foreach(addPage)
  }

  /** Note: server started this way has some limitations
    * */
  def main(args: Array[String]): Unit = {
    // start embedded Spark / Jetty server
    // defining routing will start init on its own

    object RestRoute extends Route("/rest/*") {
      object servlet extends rest.ServletRestAPIRest
      def handle(request: Request, response: Response) = {
        // maybe there is some more portable way, currently we do servlet path change only for Jetty requests
        // when running embedded, we are sure to be running Jetty, therefore this should work fine
        request.raw match {
          case req: org.eclipse.jetty.server.Request =>
            val pathInfo = req.getPathInfo
            if (pathInfo.startsWith("/rest")) {
              req.setServletPath("/rest")
            }
          case _ =>
        }
        servlet.service(request.raw, response.raw)
        response
      }
    }

    object StaticRoute extends Route("/static/*") {
      def handle(request: Request, response: Response) = {
        val filename = request.splat().head
        val path = "backend/web/static/" + filename
        val stream = new FileInputStream(new File(path))
        try {
          val out = response.raw.getOutputStream
          IOUtils.copy(stream, out)
          out.close()
        } finally {
          stream.close()
        }
        response
      }
    }

    get(RestRoute)
    post(RestRoute)
    put(RestRoute)
    delete(RestRoute)
    patch(RestRoute)
    head(RestRoute)
    connect(RestRoute)
    options(RestRoute)
    trace(RestRoute)

    get(StaticRoute)

    init()
  }

}

class ServletRouting extends SparkApplication {

  def init() = {

    ServletRouting.init()

  }


}
