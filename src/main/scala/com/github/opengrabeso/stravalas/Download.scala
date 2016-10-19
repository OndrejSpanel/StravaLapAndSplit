package com.github.opengrabeso.stravalas

import spark.{Request, Response}

@Handle(value = "/download", method = Handle.Method.Post)
object Download extends DefineRequest {

  override def html(req: Request, resp: Response) = {

    val id = req.queryParams("id")
    val op = req.queryParams("operation")
    val authToken = req.queryParams("auth_token")

    val contentType = "application/octet-stream"

    def download(export: Array[Byte], filename: String): Unit = {
      resp.status(200)
      resp.header("Content-Disposition", filename)
      resp.`type`(contentType)

      val out = resp.raw.getOutputStream
      out.write(export)
    }

    op match {
      case "split" =>
        val eventsInput = req.raw.getParameterValues("events")
        val splitTime = req.queryParams("time").toInt
        val session = req.session

        val events = session.attribute("events-" + id).asInstanceOf[Main.ActivityEvents]

        val adjusted = Main.adjustEvents(events, eventsInput)

        val split = adjusted.split(splitTime)

        split.foreach{ save =>

          val export = FitExport.export(save)

          download(export, s"attachment;filename=split_${id}_$splitTime.fit")
        }

        Nil

      case "process" =>

        val eventsInput = req.raw.getParameterValues("events")

        val events = Main.getEventsFrom(authToken, id)

        val adjusted = Main.adjustEvents(events, eventsInput)

        val export = FitExport.export(adjusted)

        download(export, s"attachment;filename=split_$id.fit")

        Nil

      case "copy" =>
        val exportUri = s"https://www.strava.com/activities/$id/export_tcx"
        /*
        val dispatcher = req.getRequestDispatcher(exportUri)
        dispatcher.forward(req, resp)
        */
        resp.redirect(exportUri)

        Nil

    }
  }
}
