package com.github.opengrabeso.stravalas
package requests

import java.net.URLEncoder

import spark.{Request, Response, Session}

object ActivityFromStrava extends DefineRequest("/activityFromStrava") with ActivityRequestHandler {

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val actId = request.queryParams("activityId")
    val activityData = Main.getEventsCachedFrom(auth, actId)

    Storage.store("events-" + actId, auth.userId, activityData, "digest" -> activityData.id.digest)

    resp.redirect(s"/selectActivity")
    Nil
  }


}
