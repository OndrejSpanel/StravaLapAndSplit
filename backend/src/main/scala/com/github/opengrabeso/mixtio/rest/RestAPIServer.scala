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

  private def sessionFileName(session: String, userId: String, file: String) = {
    Storage.FullName(Main.namespace.session(session), file, userId)
  }
  /**
    * @return session id
    */
  def createUser(auth: StravaAuthResult): StravaAuthResult = {
    Storage.store(sessionFileName(auth.sessionId, auth.userId, "auth"), auth)
    println(s"createUser ${auth.userId}, session ${auth.sessionId}")
    auth
  }

  def userAPI(userId: String, authToken: String, session: String): UserRestAPI = {
    val logging = false
    if (logging) println(s"Try userAPI for user $userId, session $session")
    val auth = Storage.load[StravaAuthResult](sessionFileName(session, userId, "auth"))
    auth.map { a =>
      if (a.token == authToken) {
        if (logging) println(s"Get userAPI for user $userId, session $session, auth.session ${a.sessionId}")
        new UserRestAPIServer(a)
      } else {
        if (logging) println("Provided auth token does not match")
        throw HttpErrorException(401, s"Provided auth token '$authToken' does not match the one stored on the server")
      }
    }.getOrElse {
      if (logging) println("User ID not authenticated")
      throw HttpErrorException(401, "User ID not authenticated. Page reload may be necessary.")
    }
  }

  def now = syncResponse {
    ZonedDateTime.now()
  }
}
