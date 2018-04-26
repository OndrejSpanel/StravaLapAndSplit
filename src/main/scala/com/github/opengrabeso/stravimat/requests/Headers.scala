package com.github.opengrabeso.stravimat
package requests

trait Headers extends DefineRequest with HtmlPart {
  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    bodyHeader(auth) ++
    super.bodyPart(req, auth) ++
    bodyFooter
  }
}
