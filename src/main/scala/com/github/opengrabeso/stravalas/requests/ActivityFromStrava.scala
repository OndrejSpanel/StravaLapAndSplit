package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object ActivityFromStrava extends DefineRequest("/activityFromStrava") with ActivityRequestHandler {

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val actId = request.queryParams("activityId")

    val stravaId = FileId.StravaId(actId.toLong)

    val activityData = stravaId match {
      case FileId.StravaId(idNum) =>
        Main.getEventsFrom(auth.token, idNum.toString)
    }

    Storage.store(stravaId.filename, auth.userId, activityData, "digest" -> activityData.id.digest)

    resp.redirect(s"/selectActivity")
    Nil
  }


}
