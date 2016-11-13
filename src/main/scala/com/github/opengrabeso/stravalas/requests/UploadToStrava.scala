package com.github.opengrabeso.stravalas
package requests

import java.io.OutputStreamWriter

import net.suunto3rdparty.strava.StravaAPI
import spark.{Request, Response}

import scala.util.parsing.json.JSONObject

object UploadToStrava extends ProcessFile("/upload-strava") {
  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit = {
    val authToken = req.queryParams("auth_token")

    val api = new StravaAPI(authToken)

    val ret = api.uploadRawFileGz(export, "fit.gz") // TODO: forward response (at least status)

    if (ret.isDefined) {
      val contentType = "application/json"
      resp.status(200)

      val json = JSONObject(Map("id" -> ret.get))

      val out = resp.raw.getOutputStream
      val writer = new OutputStreamWriter(out)
      try {
        writer.write(json.toString())
      } finally {
        writer.close()
      }

      resp.`type`(contentType)
    } else {

    }


  }


}
