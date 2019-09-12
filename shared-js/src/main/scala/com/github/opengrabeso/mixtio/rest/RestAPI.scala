package com.github.opengrabeso.mixtio
package rest

import io.udash.rest._

import scala.concurrent.Future

trait RestAPI {
  @GET
  def identity(@Path in: String): Future[String]
}

object RestAPI extends DefaultRestServerApiCompanion[RestAPI]
