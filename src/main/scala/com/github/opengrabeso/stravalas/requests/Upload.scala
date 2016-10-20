package com.github.opengrabeso.stravalas
package requests

import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import spark.{Request, Response}

@Handle(value = "/upload", method = Handle.Method.Post)
object Upload extends DefineRequest {
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

    val results = itemsIterator.flatMap { item =>
      if (!item.isFormField && "activities" == item.getFieldName) {
        val session = request.session()
        val name = item.getName
        // TODO: load stream content (TCX, FIT or GPX file)
        val stream = item.openStream()

        val extension = name.split('.').last
        extension.toLowerCase match {
          case "fit" =>
            val act = FitImport(stream)
            act.map { a =>
              session.attribute("events_name", name)
              session.attribute(s"events_$name", a)
              <p>File {name} uploaded</p>
            }.orElse(Some(<p>File {name} not uploaded, error while processing</p>))
          case e =>
            Some(<p>File {name} not uploaded, file format not supported</p>)
        }
      } else None
    }

    <html>
    <head>
      <title>File uploaded</title>
    </head>
    <body>
      {results.toList}
    </body>
    </html>

  }
}
