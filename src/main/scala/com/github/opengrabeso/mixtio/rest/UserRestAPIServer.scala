package com.github.opengrabeso.mixtio
package rest

class UserRestAPIServer extends UserRestAPI with RestAPIUtils {

  def name = {
    syncResponse("TBD")
  }

  def saveSettings(userId: String, settings: SettingsStorage) = {
    Settings.store(userId, settings)
    syncResponse(())
  }
}
