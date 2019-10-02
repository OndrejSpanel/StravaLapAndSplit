package com.github.opengrabeso.mixtio
package rest

import java.time.{Instant, ZoneId, ZoneOffset, ZonedDateTime}

import com.avsystem.commons.serialization.whenAbsent
import common.model._
import io.udash.rest._

import scala.concurrent.Future

trait UserRestAPI {
  def logout: Future[Unit]

  def settings: UserRestSettingsAPI

  @GET("settings")
  def allSettings: Future[SettingsStorage]

  @GET
  def name: Future[String]

  @GET
  def lastStravaActivities(@whenAbsent(15) count: Int): Future[Seq[ActivityId]]

  @GET
  def stagedActivities(@Query @whenAbsent(Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC)) notBefore: ZonedDateTime): Future[Seq[ActivityId]]
}

object UserRestAPI extends RestApiCompanion[EnhancedRestImplicits,UserRestAPI](EnhancedRestImplicits)