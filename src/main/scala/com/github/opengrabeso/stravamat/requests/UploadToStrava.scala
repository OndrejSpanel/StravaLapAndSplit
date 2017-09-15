package com.github.opengrabeso.stravamat
package requests

import scala.collection.JavaConverters._
import spark.{Request, Response}
import RequestUtils._




object UploadToStrava extends ProcessFile("/upload-strava") {
  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit = {
    // get form input (which splits should we upload?)

    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val api = new strava.StravaAPI(auth.token)

    val ret = api.uploadRawFileGz(export, "fit.gz") // TODO: forward response (at least status)

    if (ret.isSuccess) {
      val contentType = "application/json"
      resp.status(200)

      val output = Map("id" -> ret.get)

      jsonMapper.writeValue(resp.raw.getOutputStream, output.asJava)

      resp.`type`(contentType)
    } else {
      val contentType = "application/json"
      resp.status(400)

      val output = Map("error" -> "error")

      jsonMapper.writeValue(resp.raw.getOutputStream, output.asJava)

      resp.`type`(contentType)
    }

    Nil
  }


}
