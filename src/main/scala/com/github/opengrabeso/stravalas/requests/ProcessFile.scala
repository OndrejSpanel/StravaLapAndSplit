package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

abstract class ProcessFile(value: String, method: Method = Method.Get) extends DefineRequest(value, method) {

  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit

  override def html(req: Request, resp: Response) = {

    val id = req.queryParams("id")
    val op = req.queryParams("operation")
    val authToken = req.queryParams("auth_token")

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

          process(req, resp, export, s"attachment;filename=split_${id}_$splitTime.fit")
        }

        Nil

      case "process" =>

        val eventsInput = req.raw.getParameterValues("events")

        val events = Main.getEventsFrom(authToken, id)

        val adjusted = Main.adjustEvents(events, eventsInput)

        val export = FitExport.export(adjusted)

        process(req, resp, export, s"attachment;filename=split_$id.fit")

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
