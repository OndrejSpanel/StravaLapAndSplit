package com.github.opengrabeso.mixtio
package rest

import java.time.ZonedDateTime

import com.github.opengrabeso.mixtio.Main.{ActivityEvents, namespace}
import requests.{BackgroundTasks, MergeAndEditActivity, UploadDone, UploadDuplicate, UploadError, UploadInProgress, UploadResultToStrava}
import shared.Timing
import common.model._

class UserRestAPIServer(userAuth: Main.StravaAuthResult) extends UserRestAPI with RestAPIUtils {
  def name = syncResponse {
    userAuth.name
  }
  def settings: UserRestSettingsAPI = new UserRestSettingsAPIServer(userAuth.userId)

  def allSettings = syncResponse {
    Settings(userAuth.userId)
  }


  def logout = syncResponse {
    // TODO: delete all user info - use non-REST API
  }

  def lastStravaActivities(count: Int) = syncResponse {
    val timing = Timing.start()
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = RequestUtils.buildGetRequest(uri, userAuth.token, s"per_page=$count")

    val ret = Main.parseStravaActivities(request.execute().getContent)
    timing.logTime(s"lastStravaActivities ($count)")
    ret
  }

  def stagedActivities(notBefore: ZonedDateTime) = syncResponse {
    Main.stagedActivities(userAuth, notBefore)
  }

  def deleteActivities(ids: Seq[FileId]) = syncResponse {
    for (id <- ids) {
      Storage.delete(Storage.getFullName(Main.namespace.stage, id.filename, userAuth.userId))
      println(s"Delete ${Main.namespace.stage} ${id.filename} ${userAuth.userId}")
    }
  }

  /* Send activities from staging area to Strava, directly, with no editing, merge smart
  * */
  def sendActivitiesToStrava(ids: Seq[FileId], sessionId: String) = syncResponse {

    val activities = for {
      id <- ids
      events <- Storage.load2nd[Main.ActivityEvents](Storage.getFullName(Main.namespace.stage, id.filename, userAuth.userId))
    } yield {
      events
    }

    // TODO: move mergeAndUpload from requests
    import com.github.opengrabeso.mixtio.requests.Process

    val merged = Process.mergeForUpload(userAuth, activities)

    if (merged.nonEmpty) {
      // TODO: DRY with findMatchingStrava
      val matching = activities.flatMap { a =>
        merged.filter(_.id.isMatching(a.id)).map(a.id.id -> _.id.id)
      }

      val mergedUploadIds = Process.uploadMultiple(merged)(userAuth, sessionId)
      assert(mergedUploadIds.size == merged.size)

      val mergedToUploads = (merged.map(_.id.id) zip mergedUploadIds).toMap

      matching.map { case (source, matchingMerged) =>
        source -> mergedToUploads(matchingMerged)
      }
    }  else Nil
  }

  def processOne[T](id: FileId, events: Seq[(String, Int)], time: Int)(process: (Int, ActivityEvents) => T): Option[T] = {
    Storage.load2nd[Main.ActivityEvents](Storage.getFullName(Main.namespace.edit, id.filename, userAuth.userId)).flatMap { activity =>
      val editedEvents = events.map {
        case (ei, time) if (ei.startsWith("split")) =>
          val sportName = ei.substring("split".length)
          SplitEvent(activity.timeInActivity(time), Event.Sport.withName(sportName))
        case ("lap", time) =>
          LapEvent(activity.timeInActivity(time))
        // we should receive only laps and splits
      } :+ EndEvent(activity.endTime) // TODO: EndEvent could probably be removed completely?

      val activityToUpload = activity.copy(events = editedEvents.toArray).split(time)

      activityToUpload.map { a =>
        process(time, a)
      }
    }
  }

  def downloadEditedActivity(id: FileId, sessionId: String, events: Seq[(String, Int)], time: Int) = syncResponse {
    ???
  }

  def sendEditedActivityToStrava(id: FileId, sessionId: String, events: Seq[(String, Int)], time: Int) = syncResponse {
    val uploadIds = processOne(id, events, time) { (_, upload) =>

      val uploadFiltered = upload.applyUploadFilters(userAuth)
      // export here, or in the worker? Both is possible

      // filename is not strong enough guarantee of uniqueness, timestamp should be (in single user namespace)
      val uniqueName = uploadFiltered.id.id.filename + "_" + System.currentTimeMillis().toString
      // are any metadata needed?
      Storage.store(namespace.upload(sessionId), uniqueName, userAuth.userId, uploadFiltered.header, uploadFiltered)

      BackgroundTasks.addTask(UploadResultToStrava(uniqueName, userAuth, sessionId))

      val uploadResultNamespace = Main.namespace.uploadResult(sessionId)
      val uploadId = Storage.FullName(uploadResultNamespace, uniqueName, userAuth.userId).name
      println(s"Queued task $uniqueName with uploadId=$uploadId")
      uploadId
    }
    uploadIds
  }

  def pollUploadResults(uploadIds: Seq[String], sessionId: String) = syncResponse {
    val uploadResultNamespace = Main.namespace.uploadResult(sessionId)
    val resultsFiles = Storage.enumerate(uploadResultNamespace, userAuth.userId)

    val results = resultsFiles.flatMap { case (uploadId, resultFilename) =>
      val load = Storage.load[requests.UploadStatus](Storage.FullName(uploadResultNamespace, resultFilename, userAuth.userId))
      load.map { x =>
        val (ended, ret) = x match {
          case UploadInProgress(_) =>
            false -> UploadProgress.Pending(uploadId.name)
          case UploadDone(stravaId) =>
            true -> UploadProgress.Done(stravaId, uploadId.name)
          case UploadError(ex) =>
            true -> UploadProgress.Error(uploadId.name, ex.getLocalizedMessage)
          case UploadDuplicate(dupeId) =>
            true -> UploadProgress.Error(uploadId.name, s"Duplicate of $dupeId") // Strava no longer seems to return specific error for duplicates
        }
        if (ended) {
          // once reported, delete it
          println(s"Upload ${uploadId.name} of $resultFilename completed")
          Storage.delete(Storage.FullName(uploadResultNamespace, resultFilename, userAuth.userId))
        }
        ret
      }
    }.toSeq
    results
  }

  def mergeActivitiesToEdit(ops: Seq[FileId], sessionId: String) = syncResponse {
    val toMerge = ops.flatMap { op =>
      Storage.load[ActivityHeader, ActivityEvents](Storage.getFullName(namespace.stage, op.filename, userAuth.userId)).map(_._2.applyFilters(userAuth))
    }

    if (toMerge.nonEmpty) {
      // first merge all GPS data
      // then merge in all attribute data
      val (toMergeGPS, toMergeAttrRaw) = toMerge.partition(_.hasGPS)
      val timeOffset = Settings(userAuth.userId).questTimeOffset
      val toMergeAttr = toMergeAttrRaw.map(_.timeOffset(-timeOffset))

      val merged = if (toMergeGPS.nonEmpty) {
        val gpsMerged = toMergeGPS.reduceLeft(_ merge _)
        (gpsMerged +: toMergeAttr).reduceLeft(_ merge _)
      } else {
        toMerge.reduceLeft(_ merge _)
      }
      val mergedWithEvents = MergeAndEditActivity.saveAsNeeded(merged)(userAuth)

      val events = mergedWithEvents.events.map { e =>
        e -> merged.distanceForTime(e.stamp)
      }

      Some((mergedWithEvents.id.id, events))

    } else {
      None
    }
  }

  def routeData(id: FileId) = syncResponse {
    // TODO: consider some activity caching on the frontend/backend side
    Storage.load2nd[Main.ActivityEvents](Storage.getFullName(Main.namespace.edit, id.filename, userAuth.userId))
      .map(_.routeData).getOrElse(Nil)

  }


}
