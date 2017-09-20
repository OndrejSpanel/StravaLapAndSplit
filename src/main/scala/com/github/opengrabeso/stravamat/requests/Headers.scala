package com.github.opengrabeso.stravamat
package requests
import spark.Request

trait Headers extends DefineRequest with HtmlPart {
  override def bodyPart(req: Request, auth: Main.StravaAuthResult) = {
    bodyHeader(auth) ++
    super.bodyPart(req, auth) ++
    bodyFooter
  }
}
