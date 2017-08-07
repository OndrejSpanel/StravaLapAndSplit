package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

abstract class ProcessFile(value: String) extends DefineRequest(value) {

  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit

  override def html(req: Request, resp: Response) = {

    val id = req.queryParams("id")
    val op = req.queryParams("operation")
    val session = req.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    op match {
      case "split" =>
        val eventsInput = req.raw.getParameterValues("events")
        val splitTime = req.queryParams("time").toInt

        for (events <- Storage.load2nd[Main.ActivityEvents](Main.namespace.stage, id, auth.userId)) {

          val adjusted = Main.adjustEvents(events, eventsInput)

          val split = adjusted.split(splitTime)

          split.foreach { save =>

            val export = FitExport.export(save)

            process(req, resp, export, s"attachment;filename=split_${id}_$splitTime.fit")
          }
        }

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
