package com.github.opengrabeso.stravamat
package requests

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
case class UploadResultToStrava(key: String, auth: Main.StravaAuthResult, sessionId: String) extends DeferredTask {

  def run()= {

    val api = new strava.StravaAPI(auth.token)

    val uploadNamespace = Main.namespace.upload(sessionId)
    val uploadResultNamespace = Main.namespace.uploadResult(sessionId)

    for (upload <- Storage.load2nd[Main.ActivityEvents](uploadNamespace, key, auth.userId)) {

      val export = FitExport.export(upload)

      val ret = api.uploadRawFileGz(export, "fit.gz")

      ret match {
        case Failure(ex: HttpResponseException) if ex.getMessage.contains("duplicate of activity") =>
          // TODO: parse using regex, print info about a duplicate

          val DupeIdPattern = "duplicate of activity ([0-9]*)".r.unanchored
          val id = ex.getMessage match {
            case DupeIdPattern(dupeId) => dupeId.toLong
            case _ => 0L
          }

          Storage.store(uploadResultNamespace, key, auth.userId, UploadDuplicate(id))
        case Failure(ex) =>
          Storage.store(uploadResultNamespace, key, auth.userId, UploadError(ex))
          // https://stackoverflow.com/questions/45353793/how-to-use-deferredtaskcontext-setdonotretry-with-google-app-engine-in-java
          //DeferredTaskContext.setDoNotRetry(true)
          //throw ex
        case Success(uploadId) =>
          val queue = QueueFactory.getDefaultQueue
          println(s"Upload started $uploadId")
          val eta = System.currentTimeMillis() + 3000
          queue add TaskOptions.Builder.withPayload(WaitForStravaUpload(key, uploadId, auth, eta, sessionId))

          Storage.store(uploadResultNamespace, key, auth.userId, UploadInProgress(uploadId))
      }

      Storage.delete(uploadNamespace, key, auth.userId)


    }

  }

}

case class WaitForStravaUpload(key: String, id: Long, auth: Main.StravaAuthResult, eta: Long, sessionId: String) extends DeferredTask {
  private def retry(nextEta: Long) = {
    val queue = QueueFactory.getDefaultQueue
    queue add TaskOptions.Builder.withPayload(WaitForStravaUpload(key, id, auth, nextEta, sessionId))
  }

  def run() = {
    // check if the upload has finished
    // Strava recommends polling no more than once a second
    val now = System.currentTimeMillis()
    if (now < eta) {
      retry(eta)
    } else {
      val api = new strava.StravaAPI(auth.token)
      val uploadResultNamespace = Main.namespace.uploadResult(sessionId)
      val done = api.activityIdFromUploadId(id)
      done match {
        case Success(Some(actId)) =>
          println(s"Uploaded as $actId")
          Storage.store(uploadResultNamespace, key, auth.userId, UploadDone(actId))
        case Success(None) =>
          // still processing - retry
          retry(now + 2000)
        case Failure(ex) =>
          println(s"Upload $id failed")
          Storage.store(uploadResultNamespace, key, auth.userId, UploadError(ex))

      }

    }
  }

}