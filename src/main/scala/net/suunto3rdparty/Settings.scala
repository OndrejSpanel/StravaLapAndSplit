package net.suunto3rdparty

import com.github.opengrabeso.stravalas.{Main, Storage}

object Settings {

  @SerialVersionUID(10)
  case class SettingsStorage(questTimeOffset: Int, maxHR: Int)

  private def userSettings(userId: String) = {
    Storage.load[SettingsStorage](Main.namespace.settings, "settings", userId)
  }

  def apply(userId: String) = {
    userSettings(userId).getOrElse(SettingsStorage(0, 220))
  }

}


