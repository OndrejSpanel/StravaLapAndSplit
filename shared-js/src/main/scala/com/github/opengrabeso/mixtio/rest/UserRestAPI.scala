package com.github.opengrabeso.mixtio
package rest

import io.udash.rest._

import scala.concurrent.Future

trait UserRestAPI {
  def name: Future[String]

  @POST
  def saveSettings(@Path userId: String, settings: SettingsStorage): Future[Unit]

}

object UserRestAPI extends DefaultRestServerApiCompanion[UserRestAPI]
