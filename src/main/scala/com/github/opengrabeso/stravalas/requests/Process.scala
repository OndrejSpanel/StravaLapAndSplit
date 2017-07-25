package com.github.opengrabeso.stravalas
package requests

import com.github.opengrabeso.stravalas.Main.ActivityEvents
import org.joda.time.{DateTime => ZonedDateTime, Seconds}
import spark.{Request, Response, Session}

import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.IOUtils

import scala.xml.NodeSeq

object Process extends DefineRequest.Post("/process") {
  def saveAsNeeded(activityData: ActivityEvents)(implicit auth: Main.StravaAuthResult) = {
    activityData.id.id match {
      case id: FileId.TempId =>
        // TODO: cleanup obsolete session data
        Storage.store(id.filename, auth.userId, activityData)
      case _ =>
    }
    activityData
  }

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
      Storage.load[Main.ActivityEvents](op.filename, auth.userId)
    }


    if (toMerge.nonEmpty) {
      ???

      <html>
        <head>
          <title>Stravamat</title>
        </head>
        <body>
          Processed  ... TODO: report processing results
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