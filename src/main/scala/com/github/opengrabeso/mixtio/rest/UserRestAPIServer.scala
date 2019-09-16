package com.github.opengrabeso.mixtio
package rest

class UserRestAPIServer(userAuth: Main.StravaAuthResult) extends UserRestAPI with RestAPIUtils {
  def name = {
    syncResponse(userAuth.name)
  }
  def settings: UserRestSettingsAPI = new UserRestSettingsAPIServer(userAuth.userId)
}
