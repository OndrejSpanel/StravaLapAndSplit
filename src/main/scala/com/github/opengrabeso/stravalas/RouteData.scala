package com.github.opengrabeso.stravalas

import spark.{Request, Response}

@Handle("/route-data")
object RouteData extends DefineRequest {

  override def html(req: Request, resp: Response) = {

    val id = req.queryParams("id")
    val authToken = req.queryParams("auth_token")

    val contentType = "application/json"

    val session = req.session

    val events = session.attribute("events-"+id).asInstanceOf[Main.ActivityEvents]

    resp.`type`(contentType)
    resp.status(200)

    val out = resp.raw.getWriter
    out.write(events.routeJS)

    Nil
  }
}
