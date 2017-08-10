package com.github.opengrabeso.stravamat
package requests

import Main._
import shared.Util._
import spark.{Request, Response}

object DeleteSelected extends DefineRequest.Post("/delete-selected") with ParseFormData {
  def html(request: Request, resp: Response) = {
    val session = request.session()
    implicit val auth = session.attribute[Main.StravaAuthResult]("auth")

    val ops = activities(request)


    ops.foreach { op =>
      Storage.delete(Main.namespace.stage, op.filename, auth.userId)
      println(s"Delete ${Main.namespace.stage} ${op.filename} ${auth.userId}")
    }

    <deleted>{ops.size}</deleted>
  }
}
