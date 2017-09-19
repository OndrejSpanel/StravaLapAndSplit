package com.github.opengrabeso.stravamat
package requests

import scala.collection.JavaConverters._
import spark.{Request, Response}
import RequestUtils._




object UploadToStrava extends ProcessFile("/upload-strava") with UploadResults {

  override def html(req: Request, resp: Response) = {
    startUploadSession(req.session())

    super.html(req, resp)
  }

  override def processAll(split: Seq[(Int, Main.ActivityEvents)], id: String)(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sessionId = session.attribute[String]("sid")

    val uploadCount = uploadMultiple(split.map(_._2))(auth, sessionId)

    countResponse(uploadCount)

  }

}
