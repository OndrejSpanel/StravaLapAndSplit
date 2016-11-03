package com.github.opengrabeso.stravalas

import spark.{Request, Response, Route}
import spark.servlet.SparkApplication
import spark.Spark._

object ServletRouting {
  def route(path: String)(handleFunc: (Request, Response) => AnyRef): Route = {
    new Route(path) {
      override def handle(request: Request, response: Response) = handleFunc(request, response)
    }
  }

}

class ServletRouting extends SparkApplication {

  import ServletRouting._

  def init() {
    // add any type derived from DefineRequest here
    // solution with reflection is elegant, but overcomplicated (and hard to get working with Google App Engine) and slow
    import requests._
    val handlers: Seq[DefineRequest] = Seq(
      ActivityPage, Download, IndexHtml, RouteData, SelectActivity, Upload
    )

    def addPage(h: DefineRequest) = {
      val r = route(h.handle.value) (h.apply)
      h.handle.method match {
        case Method.Get => get(r)
        case Method.Put => put(r)
        case Method.Post => post(r)
        case Method.Delete => delete(r)
      }
    }

    handlers.foreach (addPage)
  }

}
