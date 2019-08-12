package com.github.opengrabeso.mixtio

object Settings {

  @SerialVersionUID(12)
  case class SettingsStorage(questTimeOffset: Int = 0, maxHR: Int = 220, elevFilter: Int = 2) {
    def setQuestTimeOffset(v: Option[Int]) = v.map(v => copy(questTimeOffset = v)).getOrElse(this)
    def setMaxHR(v: Option[Int]) = v.map(v => copy(maxHR = v)).getOrElse(this)
    def setElevFilter(v: Option[Int]) = v.map(v => copy(elevFilter = v)).getOrElse(this)
  }

  private def userSettings(userId: String) = {
    Storage.load[SettingsStorage](Storage.FullName(Main.namespace.settings, "settings", userId))
  }

  def store(userId: String, settings: SettingsStorage): Unit = {
    Storage.store(Storage.FullName(Main.namespace.settings, "settings", userId), settings)
  }

  def apply(userId: String): SettingsStorage = {
    userSettings(userId).getOrElse(SettingsStorage())
  }


}


