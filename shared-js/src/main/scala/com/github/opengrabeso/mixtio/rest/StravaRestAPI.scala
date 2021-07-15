package com.github.opengrabeso.mixtio
package rest

import io.udash.rest._

import scala.concurrent.Future

trait StravaRestAPI {
  @POST // used as Cloud task, Post (and body parameters, which are default for it) needed
  def uploadFile(key: String): Future[Unit]

  @POST // used as Cloud task, Post (and body parameters, which are default for it) needed
  def waitForUpload(pars: (String, Long)): Future[Unit]
}


object StravaRestAPI extends RestApiCompanion[EnhancedRestImplicits,StravaRestAPI](EnhancedRestImplicits)