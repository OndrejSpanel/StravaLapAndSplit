package com.github.opengrabeso.stravalas
package requests
import net.suunto3rdparty.Settings
import spark.{Request, Response}

object SaveSettings extends DefineRequest.Post("/save-settings") with ActivityRequestHandler {
  def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val questTimeOffset = request.queryParams("quest_time_offset").toInt
    val maxHR = request.queryParams("max_hr").toInt


    Storage.store(Main.namespace.settings, "settings", auth.userId, Settings.SettingsStorage(questTimeOffset, maxHR))
    resp.status(200)

    Nil
  }
}
