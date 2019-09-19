package com.github.opengrabeso.mixtio
package rest

import io.udash.rest._

import scala.concurrent.Future

trait UserRestAPI {
  def logout: Future[Unit]

  @GET
  def name: Future[String]

  def settings: UserRestSettingsAPI
}

object UserRestAPI extends RestApiCompanion[EnhancedRestImplicits,UserRestAPI](EnhancedRestImplicits)