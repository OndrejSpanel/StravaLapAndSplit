package com.github.opengrabeso.mixtio
package rest

import com.github.opengrabeso.mixtio.Main.StravaAuthResult
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import common.model._
import io.udash.rest.raw.HttpErrorException

import scala.collection.mutable

object RestAPIServer extends RestAPI with RestAPIUtils {

  def identity(in: String) = {
    syncResponse(in)
  }

  def createUser(auth: StravaAuthResult): StravaAuthResult = {
    val session = ServletRest.session.get
    session.setAttribute("auth", auth)
    auth
  }

  def userAPI(userId: String, authCode: String): UserRestAPI = {
    val session = ServletRest.session.get
    val auth = session.getAttribute("auth").asInstanceOf[StravaAuthResult]
    if (auth == null) {
      throw HttpErrorException(401, "User ID not authenticated. Page reload may be necessary.")
    }
    // verify user is the one who has authenticated - require the same code cookie to be used to identify the session
    if (auth.code != authCode) {
      throw HttpErrorException(401, "Provided auth code '$authCode' does not match the one stored on the server")
    } else {
      // we might store UserRestAPIServer directly
      new UserRestAPIServer(auth)
    }
  }

  def now = syncResponse {
    ZonedDateTime.now()
  }

  def elapsed(time: ZonedDateTime) = syncResponse {
    ChronoUnit.SECONDS.between(time, ZonedDateTime.now())
  }

}
