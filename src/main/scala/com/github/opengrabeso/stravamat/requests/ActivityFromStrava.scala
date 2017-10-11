package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}

object ActivityFromStrava extends DefineRequest("/activityFromStrava") with ActivityStorage {

  override def html(request: Request, resp: Response) = {
    withAuth(request, resp) { auth =>
      val actId = request.queryParams("activityId")

      val stravaId = FileId.StravaId(actId.toLong)

      val activityData = stravaId match {
        case FileId.StravaId(idNum) =>
          Main.getEventsFrom(auth.token, idNum.toString)
      }


      storeActivity(Main.namespace.stage, activityData, auth.userId)

      resp.redirect(s"/selectActivity")
      Nil
    }
  }


}
