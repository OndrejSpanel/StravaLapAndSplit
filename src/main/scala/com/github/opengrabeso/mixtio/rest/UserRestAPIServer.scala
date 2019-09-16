package com.github.opengrabeso.mixtio
package rest

class UserRestAPIServer(userAuth: Main.StravaAuthResult) extends UserRestAPI with RestAPIUtils {
  def name = {
    syncResponse(userAuth.name)
  }
  def saveSettings(settings: SettingsStorage) = {
    // userID
    Settings.store(userAuth.userId, settings)
    syncResponse(())
  }
  def saveNote(note: String) = {
    Storage.store(Storage.FullName(Main.namespace.settings, "note", userAuth.userId), note)
    syncResponse(())
  }
  def note = {
    val value = Storage.load[String](Storage.FullName(Main.namespace.settings, "note", userAuth.userId)).get
    syncResponse(value)
  }
}
