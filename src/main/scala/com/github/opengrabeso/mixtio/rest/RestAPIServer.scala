package com.github.opengrabeso.mixtio
package rest

import scala.concurrent.Future

object RestAPIServer extends RestAPI with RestAPIUtils {

  def identity(in: String) = {
    syncResponse(in)
  }

  def userId: Future[String] = {
    ???
  }


  def userAPI(userId: String): UserRestAPI = new UserRestAPIServer
}
