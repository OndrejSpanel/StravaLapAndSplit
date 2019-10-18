package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

import common.model._

object ActivityFromStrava extends DefineRequest("/activityFromStrava") with ActivityStorage {

  override def html(request: Request, resp: Response) = {
    withAuth(request, resp) { auth =>
      val actId = request.queryParams("activityId")

      val stravaId = actId.toLong

      val activityData = Main.getEventsFrom(auth.token, stravaId.toString)

      storeActivity(Main.namespace.stage, activityData, auth.userId)

      resp.redirect(s"/staging")
      Nil
    }
  }


}
