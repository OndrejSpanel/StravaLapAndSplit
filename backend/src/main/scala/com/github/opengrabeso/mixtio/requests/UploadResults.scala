package com.github.opengrabeso.mixtio
package requests

import Main._
import com.google.appengine.api.taskqueue.{QueueFactory, TaskOptions}
import spark.Session

import scala.xml._

trait UploadResults {
  def uploadResultsHtml(): NodeSeq = {
    <div id="uploads_table" style="display: none;">
      <table id="uploaded"></table>
      <h4 id="uploads_process">Processing...</h4>
      <h4 id="uploads_progress" style="display: none;">Uploading ...</h4>
      <h4 id="uploads_complete" style="display: none;">Complete</h4>
    </div>
      <script src="static/uploadProgress.js"></script>
  }


  def uploadMultiple(merged: Seq[ActivityEvents])(auth: StravaAuthResult, sessionId: String): Seq[String] = {
    // store everything into a session storage, and make background tasks to upload it to Strava

    val queue = QueueFactory.getDefaultQueue
    for (upload <- merged) yield {
      val uploadFiltered = upload.applyUploadFilters(auth)
      // export here, or in the worker? Both is possible

      // filename is not strong enough guarantee of uniqueness, timestamp should be (in single user namespace)
      val uniqueName = uploadFiltered.id.id.filename + "_" + System.currentTimeMillis().toString
      // are any metadata needed?
      Storage.store(namespace.upload(sessionId), uniqueName, auth.userId, uploadFiltered.header, uploadFiltered)

      // using post with param is not recommended, but it should be OK when not using any payload
      queue add TaskOptions.Builder.withPayload(UploadResultToStrava(uniqueName, auth, sessionId))

      val uploadResultNamespace = Main.namespace.uploadResult(sessionId)
      val uploadId = Storage.FullName(uploadResultNamespace, uniqueName, auth.userId).name
      println(s"Queued task $uniqueName with uploadId=$uploadId")
      uploadId
    }
  }

  def countResponse(count: Int): NodeSeq = {
    // used in AJAX only - XML response
    <upload>
      <count>{count.toString}</count>
    </upload>

  }

  def startUploadSession(session: Session) = {
    // Strava upload progress session id
    val sid = System.currentTimeMillis().toString
    session.attribute("sid", sid)

  }
}
