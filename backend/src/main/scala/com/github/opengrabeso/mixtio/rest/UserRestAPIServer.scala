package com.github.opengrabeso.mixtio
package rest

import java.time.ZonedDateTime

import shared.Timing
import common.model._

object UserRestAPIServer {
  implicit class ModelConversion(aid: Main.ActivityId) {
    def toModel: ActivityIdModel = {
      ActivityIdModel(aid.id.toString, aid.digest, aid.name, aid.startTime.toString, aid.endTime.toString, aid.sportName.toString, aid.distance)
    }
  }
}

import UserRestAPIServer._

class UserRestAPIServer(userAuth: Main.StravaAuthResult) extends UserRestAPI with RestAPIUtils {
  def name = {
    syncResponse(userAuth.name)
  }
  def settings: UserRestSettingsAPI = new UserRestSettingsAPIServer(userAuth.userId)

  def logout = {
    // TODO: delete all user info - use non-REST API
    syncResponse(())
  }

  def lastStravaActivities(count: Int) = {
    val timing = Timing.start()
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = RequestUtils.buildGetRequest(uri, userAuth.token, s"per_page=$count")

    val ret = Main.parseStravaActivities(request.execute().getContent)
    timing.logTime(s"lastStravaActivities ($count)")
    syncResponse(ret.map(_.toModel))
  }

  def stagedActivities(notBefore: ZonedDateTime) = {
    syncResponse(Main.stagedActivities(userAuth, notBefore).map(_.id.toModel))
  }

}
