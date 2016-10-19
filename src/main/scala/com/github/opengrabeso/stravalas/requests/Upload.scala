package com.github.opengrabeso.stravalas
package requests

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

    while (items.hasNext) {
      val item=items.next()
      if (!item.isFormField && "activities" == item.getFieldName) {
        // TODO: get filename
        val session = request.session()
        session.attribute("KEY_FILE_NAME", item.getName)
        // TODO: load stream content (TCX, FIT or GPX file)
        val stream = item.openStream()
      }
    }

    // Parse the request

    <html>
      <head>
        <title>File uploaded</title>
      </head>
      <body>
        <p>File uploaded</p>
      </body>
    </html>
  }
}
