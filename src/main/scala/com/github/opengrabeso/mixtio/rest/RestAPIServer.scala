package com.github.opengrabeso.mixtio
package rest

import com.github.opengrabeso.mixtio.Main.StravaAuthResult
import io.udash.rest._

import scala.collection.mutable
import scala.concurrent.Future

object RestAPIServer extends RestAPI with RestAPIUtils {

  // TODO: consider some user cleanup, as authCode is expired

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
}
