package com.github.opengrabeso.stravalas
package requests

import scala.collection.JavaConverters._
import net.suunto3rdparty.strava.StravaAPI
import spark.{Request, Response}
import RequestUtils._
import com.github.opengrabeso.stravalas.Main.ActivityEvents

object UploadResultToStrava extends DefineRequest.Post("/upload-result") {
  def html(req: Request, resp: Response) = {

    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val key = req.queryParams("key")

    val api = new StravaAPI(auth.token)

    val upload = session.attribute[ActivityEvents](key)

    val export = FitExport.export(upload)

    val ret = api.uploadRawFileGz(export, "fit.gz")

    if (ret.isDefined) {
      val contentType = "application/json"
      resp.status(200)

      val output = Map("id" -> ret.get) // this is upload id, not file id - TODO: we need to wait for that (using a task?)

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
