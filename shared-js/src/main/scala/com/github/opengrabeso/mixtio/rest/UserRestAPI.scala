package com.github.opengrabeso.mixtio
package rest

import com.avsystem.commons.serialization.whenAbsent
import common.model._
import io.udash.rest._

import scala.concurrent.Future

trait UserRestAPI {
  def logout: Future[Unit]

  def settings: UserRestSettingsAPI

  @GET
  def name: Future[String]

  @GET
  def lastStravaActivities(@whenAbsent(10) count: Int): Future[Seq[ActivityIdModel]]
}

object UserRestAPI extends RestApiCompanion[EnhancedRestImplicits,UserRestAPI](EnhancedRestImplicits)