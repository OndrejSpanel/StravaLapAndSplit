package com.github.opengrabeso.mixtio
package rest

import com.github.opengrabeso.mixtio.Main.StravaAuthResult
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import scala.collection.mutable

object RestAPIServer extends RestAPI with RestAPIUtils {

  val users = mutable.Map.empty[String, StravaAuthResult]

  def identity(in: String) = {
    syncResponse(in)
  }

  def createUser(auth: StravaAuthResult): StravaAuthResult = {
    synchronized {
      users(auth.id) = auth
    }
    auth
  }

  def userAPI(userId: String): UserRestAPI = {
    val auth = synchronized {
      users(userId)
    }
    // we might store UserRestAPIServer directly
    new UserRestAPIServer(auth)
  }

  def now = syncResponse {
    ZonedDateTime.now()
  }

  def elapsed(time: ZonedDateTime) = syncResponse {
    ChronoUnit.SECONDS.between(time, ZonedDateTime.now())
  }

}
