package com.github.opengrabeso.mixtio
package rest

import io.udash.rest._

import scala.concurrent.Future

trait UserRestAPI {
  @GET
  def name: Future[String]

  @POST
  def saveSettings(settings: SettingsStorage): Future[Unit]

  /**
    * Testing API only - for debugging REST interface
    * */
  @PUT("note")
  def saveNote(note: String): Future[Unit]

  @GET
  def note: Future[String]
}

object UserRestAPI extends DefaultRestServerApiCompanion[UserRestAPI]
