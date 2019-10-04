package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object UploadToStrava extends ProcessFile("/upload-strava") with UploadResults {

  override def html(req: Request, resp: Response) = {
    startUploadSession(req.session())

    super.html(req, resp)
  }

  override def processAll(split: Seq[(Int, Main.ActivityEvents)], id: String)(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sessionId = session.attribute[String]("sid")

    val uploadCount = uploadMultiple(split.map(_._2))(auth, sessionId).size

    countResponse(uploadCount)

  }

}
