package com.github.opengrabeso.stravalas
package requests

import net.suunto3rdparty.strava.StravaAPI
import com.google.api.client.http.HttpResponseException
import com.google.appengine.api.taskqueue._

import scala.util.{Failure, Success}
import scala.xml.NodeSeq


trait UploadStatus {
  def xml: NodeSeq
}

@SerialVersionUID(10)
case class UploadInProgress(uploadId: Long) extends UploadStatus {
  def xml = Nil
}

@SerialVersionUID(10)
case class UploadDone(stravaId: Long) extends UploadStatus {
  def xml = <done>{stravaId}</done>
}

@SerialVersionUID(10)
case class UploadDuplicate(dupeId: Long) extends UploadStatus {
  def xml = <duplicate>{dupeId}</duplicate>
}

@SerialVersionUID(10)
case class UploadError(ex: Throwable) extends UploadStatus {
  def xml = <error>{ex.getMessage}</error>

}
// background push queue task

@SerialVersionUID(10L)
case class UploadResultToStrava(key: String, auth: Main.StravaAuthResult) extends DeferredTask {

  def run()= {

    val api = new StravaAPI(auth.token)

    for (upload <- Storage.load[Main.ActivityEvents](Main.namespace.upload, key, auth.userId)) {

      val export = FitExport.export(upload)

      val ret = api.uploadRawFileGz(export, "fit.gz")

      ret match {
        case Failure(ex: HttpResponseException) if ex.getMessage.contains("duplicate of activity") =>
          // TODO: parse using regex, print info about a duplicate
          Storage.store(Main.namespace.uploadResult, key, auth.userId, UploadDuplicate(0))
        case Failure(ex) =>
          Storage.store(Main.namespace.uploadResult, key, auth.userId, UploadError(ex))
          // https://stackoverflow.com/questions/45353793/how-to-use-deferredtaskcontext-setdonotretry-with-google-app-engine-in-java
          //DeferredTaskContext.setDoNotRetry(true)
          //throw ex
        case Success(uploadId) =>
          val queue = QueueFactory.getDefaultQueue
          println(s"Upload started $uploadId")
          val eta = System.currentTimeMillis() + 3000
          queue add TaskOptions.Builder.withPayload(WaitForStravaUpload(uploadId, auth, eta))

          Storage.store(Main.namespace.uploadResult, key, auth.userId, UploadInProgress(uploadId))
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