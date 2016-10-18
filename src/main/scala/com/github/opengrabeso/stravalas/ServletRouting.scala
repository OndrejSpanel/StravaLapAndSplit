package com.github.opengrabeso.stravalas

import org.eclipse.jetty.server.ServletResponseHttpWrapper
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
    get(route("*.jsp") { (request, response) =>
        "JSP response goes here"
    })

    get(route("/test") { (request, response) =>
      "response goes here"
    })
  }

}
