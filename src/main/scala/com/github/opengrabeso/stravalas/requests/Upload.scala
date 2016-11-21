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


    val data = itemsIterator.flatMap { item =>
      if (!item.isFormField && "activities" == item.getFieldName) {
        val name = item.getName
        val stream = item.openStream()

        val extension = item.getName.split('.').last
        extension.toLowerCase match {
          case "fit" =>
            FitImport(stream).map(name -> _)
          case e =>
            None
        }
      } else None
    }

    if (data.hasNext) {
      val d = data.foldLeft(data.next) {
        (total, d) =>
          total._1 -> total._2.merge(d._2)
      }

      // TODO: pass data directly to JS?
      session.attribute("events-" + d._1, d._2)
      val content = htmlHelper(d._1, d._2, session, resp)

      <html>
        <head>
          {headPrefix}
          <title>Stravamat</title>
          {content.head}
        </head>
        <body>
          {bodyHeader(auth)}
          {content.body}
          {bodyFooter}
        </body>
      </html>

    } else {
      <html>
        <head>
          {headPrefix}
          <title>Stravamat</title>
        </head>
        <body>
          {bodyHeader(auth)}
          <p>Empty activity</p>
          {bodyFooter}
        </body>
      </html>
    }


  }
}
