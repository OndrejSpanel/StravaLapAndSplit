package com.github.opengrabeso.stravalas
package requests

import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import spark.{Request, Response}

object Upload extends DefineRequest("/upload", method = Method.Post) with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val fif = new DiskFileItemFactory()
    val maxMB = 32
    fif.setSizeThreshold(maxMB * 1024 * 1024)

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext
      def next() = items.next
    }

    itemsIterator.foreach { item =>
      if (!item.isFormField && "activities" == item.getFieldName) {
        val name = item.getName
        val stream = item.openStream()

        val extension = item.getName.split('.').last
        val actData: Seq[(String, Main.ActivityEvents)] = extension.toLowerCase match {
          case "fit" =>
            FitImport(stream).map(name -> _).toSeq
          case "sml" =>
            MoveslinkImport.loadSml(name, stream).map(name -> _)
          case "xml" =>
            MoveslinkImport.loadXml(name, stream).map(name -> _)
          case e =>
            Nil
        }
        for (act <- actData) {
          Storage.store("events-" + act._2.id.id, auth.userId, act._2)
        }
      }
    }

    resp.redirect("/selectActivity")
    Nil
  }
}
