package com.github.opengrabeso.stravalas
package requests

import net.suunto3rdparty.strava.StravaAPI
import spark.{Request, Response}

object UploadToStrava extends ProcessFile("/upload-strava") {
  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit = {
    val authToken = req.queryParams("auth_token")

    val api = new StravaAPI(authToken)

    api.uploadRawFileGz(export, ".fit.gz") // TODO: forward response (at least status)

    val contentType = "application/octet-stream"
    resp.status(200)
    resp.header("Content-Disposition", filename)
    resp.`type`(contentType)

    val out = resp.raw.getOutputStream
    out.write(export)
  }


}
