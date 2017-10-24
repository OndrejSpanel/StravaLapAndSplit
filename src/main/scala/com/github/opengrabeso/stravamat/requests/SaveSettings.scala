package com.github.opengrabeso.stravamat
package requests
import spark.{Request, Response}

object SaveSettings extends DefineRequest.Post("/save-settings") {
  def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    def loadSettingsInt(name: String) = {
      Option(request.queryParams(name)).map(_.toInt)
    }

    val settings = Settings(auth.userId)
      .setQuestTimeOffset(loadSettingsInt("quest_time_offset"))
      .setMaxHR(loadSettingsInt("max_hr"))
      .setElevFilter(loadSettingsInt("elev_filter"))

    Settings.store(auth.userId, settings)
    resp.status(200)

    Nil
  }
}
