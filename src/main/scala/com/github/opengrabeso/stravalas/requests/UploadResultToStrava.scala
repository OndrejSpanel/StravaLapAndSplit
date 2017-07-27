package com.github.opengrabeso.stravalas
package requests

import scala.collection.JavaConverters._
import net.suunto3rdparty.strava.StravaAPI
import spark.{Request, Response}
import RequestUtils._
import com.github.opengrabeso.stravalas.Main.ActivityEvents
import com.google.appengine.api.taskqueue._

// background push queue task

@SerialVersionUID(10L)
case class UploadResultToStrava(key: String, auth: Main.StravaAuthResult) extends DeferredTask {

  def run()= {

    val api = new StravaAPI(auth.token)

    for (upload <- Storage.load[Main.ActivityEvents](Main.namespace.upload, key, auth.userId)) {

      val export = FitExport.export(upload)

      //val ret = api.uploadRawFileGz(export, "fit.gz")
      val ret = api.uploadRawFile(export, "fit")

      ret.fold {
        println("Upload not started")
      } { uploadId =>
        val output = Map("id" -> uploadId) // this is upload id, not file id - TODO: we need to wait for that (using a task?)
        println(s"Upload started: $output $uploadId")
      }
    }

  }

}
