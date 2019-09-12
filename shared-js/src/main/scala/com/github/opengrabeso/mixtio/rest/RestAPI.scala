package com.github.opengrabeso.mixtio
package rest

import com.sun.scenario.Settings
import io.udash.rest._

import scala.concurrent.Future

trait RestAPI {
  @GET
  def identity(@Path in: String): Future[String]

  @POST
  def saveSettings(@Path userId: String, settings: SettingsStorage): Future[Unit]
}

object RestAPI extends DefaultRestServerApiCompanion[RestAPI]
