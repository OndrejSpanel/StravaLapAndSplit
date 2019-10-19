package com.github.opengrabeso.mixtio
package requests

import Main._
import common.model._
import java.time.ZonedDateTime
import spark.{Request, Response, Session}
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload

import scala.xml.NodeSeq

object MergeAndEditActivity {
  def saveAsNeeded(activityData: ActivityEvents)(implicit auth: StravaAuthResult) = {
    val prepare = activityData.cleanPositionErrors.processPausesAndEvents
    // TODO: make sure edited name is unique
    Storage.store(namespace.edit, prepare.id.id.filename, auth.userId, prepare.header, prepare)
    prepare
  }
}
