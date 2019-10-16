package com.github.opengrabeso.mixtio
package rest

import java.time.ZonedDateTime
import io.udash.rest._

import scala.concurrent.Future

trait RestAPI {
  @GET
  def identity(@Path in: String): Future[String]

  @Prefix("user")
  def userAPI(@Path userId: String, @Cookie authCode: String): UserRestAPI

  @GET
  def now: Future[ZonedDateTime]

  @GET
  def elapsed(time: ZonedDateTime): Future[Long]
}

object RestAPI extends RestApiCompanion[EnhancedRestImplicits,RestAPI](EnhancedRestImplicits)
