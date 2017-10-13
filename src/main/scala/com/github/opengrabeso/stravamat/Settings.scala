package com.github.opengrabeso.stravamat

object Settings {

  @SerialVersionUID(10)
  case class SettingsStorage(questTimeOffset: Int, maxHR: Int)

  private def userSettings(userId: String) = {
    Storage.load[SettingsStorage](Storage.FullName(Main.namespace.settings, "settings", userId))
  }

  def store(userId: String, settings: SettingsStorage): Unit = {
    Storage.store(Storage.FullName(Main.namespace.settings, "settings", userId), settings)
  }


  def apply(userId: String): SettingsStorage = {
    userSettings(userId).getOrElse(SettingsStorage(0, 220))
  }

}


