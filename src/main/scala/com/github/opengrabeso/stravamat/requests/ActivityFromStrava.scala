package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}

object ActivityFromStrava extends DefineRequest("/activityFromStrava") {

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val actId = request.queryParams("activityId")

    val stravaId = FileId.StravaId(actId.toLong)

    val activityData = stravaId match {
      case FileId.StravaId(idNum) =>
        Main.getEventsFrom(auth.token, idNum.toString)
    }

    Storage.store(Main.namespace.stage, stravaId.filename, auth.userId, activityData.header, activityData, "digest" -> activityData.id.digest)

    resp.redirect(s"/selectActivity")
    Nil
  }


}
