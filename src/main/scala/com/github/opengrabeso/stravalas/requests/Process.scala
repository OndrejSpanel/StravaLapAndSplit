package com.github.opengrabeso.stravalas
package requests

import com.github.opengrabeso.stravalas.Main.ActivityEvents
import net.suunto3rdparty.moveslink.MovesLinkUploader
import spark.{Request, Response, Session}
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload

import com.google.appengine.api.taskqueue._

import net.suunto3rdparty.Util._

object Process extends DefineRequest.Post("/process") {
  override def html(request: Request, resp: Response) = {

    val session = request.session()
    implicit val auth = session.attribute[Main.StravaAuthResult]("auth")

    val fif = new DiskFileItemFactory()
    fif.setSizeThreshold(1 * 1024) // we do not expect any files, only form parts

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext

      def next() = items.next
    }

    val ops = itemsIterator.flatMap { item =>
      if (item.isFormField) {
        // expect field name id={FileId}
        val IdPattern = "id=(.*)".r
        val id = item.getFieldName match {
          case IdPattern(idText) =>
            Some(FileId.parse(idText))
          case _ =>
            None
        }
        /*
        //println(item)
        val is = item.openStream()
        val itemContent = try {
          IOUtils.toString(is)
        } finally {
          is.close()
        }
        */
        id
      } else {
        None
      }
    }.toVector

    // TODO: create groups, process each group separately
    val toMerge = ops.flatMap { op =>
      Storage.load[Main.ActivityEvents](Main.namespace.stage, op.filename, auth.userId)
    }


    if (toMerge.nonEmpty) {

      val (gpsMoves, attrMovesRaw) = toMerge.partition(_.hasGPS)

      val timeOffset = net.suunto3rdparty.Settings.questTimeOffset
      val ignoreDuration = 30

      val attrMoves = attrMovesRaw.map(_.timeOffset(-timeOffset))

      def filterIgnored(x : ActivityEvents) = x.isAlmostEmpty(ignoreDuration)

      val timelineGPS = gpsMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)
      val timelineAttr = attrMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)

      val merged = MovesLinkUploader.processTimelines(timelineGPS, timelineAttr)

      // store everything into a session storage, and make background tasks to upload it to Strava


      // [START addQueue]
      val queue = QueueFactory.getDefaultQueue
      for (upload <- merged) {

        val export = FitExport.export(upload)

        // filename is not strong enough guarantee of uniqueness, timestamp should be (in single user namespace)
        val uniqueName = upload.id.id.filename + "_" + System.currentTimeMillis().toString
        // are any metadata needed?
        Storage.store(Main.namespace.upload, uniqueName, auth.userId, export)

        // using post with param is not recommended, but it should be OK when not using any payload
        queue add TaskOptions.Builder.withUrl("/upload-result").method(TaskOptions.Method.POST).param("key", uniqueName)
        println(s"Queued task $uniqueName")
      }


      <html>
        <head>
          <title>Stravamat</title>
        </head>
        <body>
          Sent {merged.size.toString} files ... TODO: report processing results
        </body>
      </html>
    }
    else {
      <html>
        <head>
          <title>Stravamat</title>{headPrefix}
        </head>
        <body>
          Empty - no activity selected
        </body>
      </html>
    }
  }

}