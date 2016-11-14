package com.github.opengrabeso.stravalas
package requests

import scala.collection.JavaConverters._
import net.suunto3rdparty.strava.StravaAPI
import spark.{Request, Response}
import RequestUtils._

object UploadToStrava extends ProcessFile("/upload-strava") {
  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit = {
    val authToken = req.queryParams("auth_token")

    val api = new StravaAPI(authToken)

    val ret = api.uploadRawFileGz(export, "fit.gz") // TODO: forward response (at least status)

    if (ret.isDefined) {
      val contentType = "application/json"
      resp.status(200)

      val output = Map("id" -> ret.get)

      jsonMapper.writeValue(resp.raw.getOutputStream, output.asJava)

      resp.`type`(contentType)
    } else {

    }


  }


}
