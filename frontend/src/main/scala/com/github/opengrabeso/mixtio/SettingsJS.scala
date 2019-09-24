package com.github.opengrabeso.mixtio

import scala.scalajs.js
import js.annotation._

object SettingsJS {
  @JSExportTopLevel("settingsChanged")
  def settingsChanged(name: String, v: String, userId: String): Unit = {
    val settingsAPI = rest.RestAPIClient.api.userAPI(userId).settings
    name match {
      case "quest_time_offset" => settingsAPI.quest_time_offset(v.toInt)
      case "max_hr" => settingsAPI.max_hr(v.toInt)
      case "elev_filter" => settingsAPI.elev_filter(v.toInt)
    }
  }
}
