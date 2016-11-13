package com.github.opengrabeso.stravalas
package requests

import java.io.OutputStreamWriter

import spark.{Request, Response}

object RouteData extends DefineRequest("/route-data") {

  override def html(req: Request, resp: Response) = {
    val session = req.session

    val id = req.queryParams("id")

    val contentType = "application/json"
    val events = session.attribute("events-"+id).asInstanceOf[Main.ActivityEvents]

    if (events != null) {
      resp.`type`(contentType)
      resp.status(200)

      val out = resp.raw.getOutputStream
      val writer = new OutputStreamWriter(out)
      try {
        writer.write(events.routeJS)
      } finally {
        writer.close()
      }
    } else {
      resp.status(404)
    }

    Nil
  }
}
