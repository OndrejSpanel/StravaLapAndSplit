package com.github.opengrabeso.stravamat
package requests
import spark.{Request, Response}

object SaveSettings extends DefineRequest.Post("/save-settings") {
  def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val questTimeOffset = request.queryParams("quest_time_offset").toInt
    val maxHR = request.queryParams("max_hr").toInt


    Storage.store(Storage.FullName(Main.namespace.settings, "settings", auth.userId), Settings.SettingsStorage(questTimeOffset, maxHR))
    resp.status(200)

    Nil
  }
}
