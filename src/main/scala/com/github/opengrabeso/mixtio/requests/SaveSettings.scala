package com.github.opengrabeso.mixtio
package requests
import spark.{Request, Response}

import scala.concurrent.Future

object SaveSettings extends DefineRequest.Post("/save-settings") {
  def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    def processSetting(name: String, f: Int => Future[Unit]) = {
      for (value <- Option(request.queryParams(name))) {
        f(value.toInt)
      }
    }

    val settingsAPI = rest.RestAPIServer.userAPI(auth.userId).settings
    processSetting("quest_time_offset", settingsAPI.quest_time_offset)
    processSetting("max_hr", settingsAPI.max_hr)
    processSetting("elev_filter", settingsAPI.elev_filter)

    resp.status(200)

    Nil
  }
}
