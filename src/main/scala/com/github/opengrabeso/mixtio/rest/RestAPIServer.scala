package com.github.opengrabeso.mixtio
package rest

import scala.concurrent.Future

object RestAPIServer extends RestAPI {
  def identity(in: String) = Future.successful(in)
}
