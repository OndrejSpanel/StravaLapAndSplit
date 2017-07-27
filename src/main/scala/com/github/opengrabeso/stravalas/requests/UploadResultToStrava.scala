package com.github.opengrabeso.stravalas
package requests

import scala.collection.JavaConverters._
import net.suunto3rdparty.strava.StravaAPI
import spark.{Request, Response}
import RequestUtils._
import com.github.opengrabeso.stravalas.Main.ActivityEvents
import com.google.appengine.api.taskqueue._

import scala.util.{Failure, Success}

// background push queue task

@SerialVersionUID(10L)
case class UploadResultToStrava(key: String, auth: Main.StravaAuthResult) extends DeferredTask {

  def run()= {

    val api = new StravaAPI(auth.token)

    for (upload <- Storage.load[Main.ActivityEvents](Main.namespace.upload, key, auth.userId)) {

      val export = FitExport.export(upload)

      val ret = api.uploadRawFileGz(export, "fit.gz")

      ret match {
        case Failure(ex) =>
          println("Upload not started")
          // https://stackoverflow.com/questions/45353793/how-to-use-deferredtaskcontext-setdonotretry-with-google-app-engine-in-java
          //DeferredTaskContext.setDoNotRetry(true)
          //throw ex
        case Success(uploadId) =>
          val queue = QueueFactory.getDefaultQueue
          println(s"Upload started $uploadId")
          val eta = System.currentTimeMillis() + 3000
          queue add TaskOptions.Builder.withPayload(WaitForStravaUpload(uploadId, auth, eta))

      }

      Storage.delete(Main.namespace.upload, key, auth.userId)
    }

  }

}

case class WaitForStravaUpload(id: Long, auth: Main.StravaAuthResult, eta: Long) extends DeferredTask {
  private def retry(nextEta: Long) = {
    val queue = QueueFactory.getDefaultQueue
    queue add TaskOptions.Builder.withPayload(WaitForStravaUpload(id, auth, nextEta))
  }

  def run() = {
    // check if the upload has finished
    // Strava recommends polling no more than once a second
    val now = System.currentTimeMillis()
    if (now < eta) {
      retry(eta)
    } else {
      val api = new StravaAPI(auth.token)
      val done = api.activityIdFromUploadId(id)
      done match {
        case Left(actId) =>
          println(s"Uploaded as $actId")
        case Right(true) =>
          // still processing - retry
          retry(now + 2000)
        case _ =>
          println(s"Upload $id failed")

      }

    }
  }

}