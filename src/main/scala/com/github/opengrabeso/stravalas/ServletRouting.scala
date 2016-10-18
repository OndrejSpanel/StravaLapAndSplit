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
    get(route("/") { (request, response) =>
      IndexHtml(request)
    })

    get(route("/index") { (request, response) =>
      IndexHtml(request)
    })

    get(route("/selectActivity") { (request, response) =>
      SelectActivity(request)
    })

    get(route("/activity") { (request, response) =>
      Activity(request)
    })
  }

}
