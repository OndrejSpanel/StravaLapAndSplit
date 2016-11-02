package com.github.opengrabeso.stravalas
package requests

import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import spark.{Request, Response}

@Handle(value = "/upload", method = Handle.Method.Post)
object Upload extends DefineRequest with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {

    val fif = new DiskFileItemFactory()
    val maxMB = 32
    fif.setSizeThreshold(maxMB * 1024 * 1024)

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext
      def next() = items.next
    }

    val uploadedName = "uploaded"

    val data = itemsIterator.flatMap { item =>
      if (!item.isFormField && "activities" == item.getFieldName) {
        val session = request.session()
        val name = uploadedName
        val stream = item.openStream()

        val extension = item.getName.split('.').last
        extension.toLowerCase match {
          case "fit" =>
            FitImport(stream)
          case e =>
            None
        }
      } else None
    }

    // TODO: handle multiple uploaded files
    val d = data.toSeq.head

    // TODO: pass data directly to JS?
    request.session.attribute("events-" + uploadedName, d)
    val content = htmlHelper(uploadedName, d, request.session, resp)

    <html>
      <head>
        <meta charset="utf-8"/>
        <title>Strava Split And Lap</title>
        {content.head}
      </head>
      <body>
        {content.body}
      </body>
    </html>

  }
}
