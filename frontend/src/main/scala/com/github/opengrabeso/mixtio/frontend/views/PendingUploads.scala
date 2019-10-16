package com.github.opengrabeso.mixtio
package frontend.views

import com.github.opengrabeso.mixtio.common.model.{FileId, UploadProgress}
import com.github.opengrabeso.mixtio.frontend.views.select.PagePresenter.delay

import scala.concurrent.ExecutionContext

abstract class PendingUploads(implicit ec: ExecutionContext) {
  var pending = Map.empty[String, Set[FileId]]

  private final val pollPeriodMs = 1000

  def startUpload(api: rest.UserRestAPI, fileIds: Seq[FileId]) = {

    val setOfFileIds = fileIds.toSet
    // set all files as uploading before starting the API to make the UI response immediate
    setUploadProgressFile(setOfFileIds, true, "Uploading...")

    val uploadStarted = api.sendActivitiesToStrava(fileIds, facade.UdashApp.sessionId)

    uploadStarted.foreach { a =>
      val add = a.toMap
      // some activities might be discarded, fileId is not guaranteed to match fileToPending
      // remove uploading status for the files for which no upload has started
      setUploadProgressFile(setOfFileIds -- add.keySet, false, "")

      add.foreach { case (id, i) =>
        println(s"Upload $i started for $id")
        pending += pending.get(i).map { addTo =>
          i -> (addTo + id)
        }.getOrElse {
          i -> Set(id)
        }
      }
      println(s"pending ${pending.size} (added ${add.size})")
      if (pending.nonEmpty) {
        delay(pollPeriodMs).foreach(_ => checkPendingResults(api))
      }
    }

  }

  def checkPendingResults(api: rest.UserRestAPI): Unit = {
    for {
      status <- api.pollUploadResults(pending.keys.toSeq, facade.UdashApp.sessionId)
    } {
      for (result <- status) {
        result match {
          case UploadProgress.Pending(uploadId) =>
          case UploadProgress.Done(stravaId, uploadId) =>
            println(s"$uploadId completed with $result")
            setStrava(uploadId, Some(FileId.StravaId(stravaId)))
            setUploadProgress(uploadId, false, "")
            pending -= uploadId
          case UploadProgress.Error(uploadId, error) =>
            println(s"$uploadId completed with error $error")
            setUploadProgress(uploadId, true, error)
            pending -= uploadId
        }
      }
      if (pending.nonEmpty) {
        delay(pollPeriodMs).foreach(_ => checkPendingResults(api))
      }
    }
  }

  def setStrava(uploadId: String, stravaId: Option[FileId.StravaId]): Unit = {
    for (fileId <- pending.get(uploadId)) {
      setStravaFile(fileId, stravaId)
    }
  }

  def setUploadProgress(uploadId: String, uploading: Boolean, uploadState: String): Unit = {
    for (fileId <- pending.get(uploadId)) {
      setUploadProgressFile(fileId, uploading, uploadState)
    }
  }

  def setUploadProgressFile(fileId: Set[FileId], uploading: Boolean, uploadState: String): Unit
  def setStravaFile(fileId: Set[FileId], stravaId: Option[FileId.StravaId]): Unit

}
