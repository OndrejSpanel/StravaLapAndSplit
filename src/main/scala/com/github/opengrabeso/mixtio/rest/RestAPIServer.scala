package com.github.opengrabeso.mixtio
package rest

object RestAPIServer extends RestAPI with RestAPIUtils {

  def identity(in: String) = {
    syncResponse(in)
  }
  def saveSettings(userId: String, settings: SettingsStorage) = {
    Settings.store(userId, settings)
    syncResponse(())
  }
}
