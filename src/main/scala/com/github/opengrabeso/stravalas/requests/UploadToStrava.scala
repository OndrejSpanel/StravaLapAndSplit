package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object UploadToStrava extends DefineRequest with ActivityRequestHandler {
  def handle = Handle("/upload-strava")

  override def html(request: Request, resp: Response) = {
    ???
  }

}
