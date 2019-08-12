package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object DeleteSelected extends DefineRequest.Post("/delete-selected") with ParseFormData {
  def html(request: Request, resp: Response) = {
    val session = request.session()
    implicit val auth = session.attribute[Main.StravaAuthResult]("auth")

    val ops = activities(request)._1


    ops.foreach { op =>
      Storage.delete(Storage.getFullName(Main.namespace.stage, op.filename, auth.userId))
      println(s"Delete ${Main.namespace.stage} ${op.filename} ${auth.userId}")
    }

    <deleted>{ops.size}</deleted>
  }
}
