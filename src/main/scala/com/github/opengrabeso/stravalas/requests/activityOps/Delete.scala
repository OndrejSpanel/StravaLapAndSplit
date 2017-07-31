package com.github.opengrabeso.stravalas
package requests
package activityOps


import spark.{Request, Response}

object Delete extends DefineRequest.Post("/delete") {

  override def html(req: Request, resp: Response) = {
    val session = req.session

    val id = req.queryParams("id")
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val fid = FileId.parse(id)
    // delete from staging area only, not from the uploaded (will be uploaded again unless files are deleted)
    if (Storage.delete(Main.namespace.stage, fid.filename, auth.userId)) {

    } else {
      resp.status(404)
    }

    Nil
  }
}
