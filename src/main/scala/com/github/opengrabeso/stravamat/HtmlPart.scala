package com.github.opengrabeso.stravamat

import scala.xml.NodeSeq

trait HtmlPart extends DefineRequest {
  final type Request = spark.Request
  final type Response = spark.Response
  final type StravaAuthResult = Main.StravaAuthResult

  def bodyPart(req: Request, auth: StravaAuthResult): NodeSeq
  def headerPart(req: Request, auth: StravaAuthResult): NodeSeq

}

trait HtmlByParts extends DefineRequest with HtmlPart {
  def headerPart(req: Request, auth: StravaAuthResult): NodeSeq = NodeSeq.Empty

  def bodyPart(req: Request, auth: StravaAuthResult): NodeSeq = NodeSeq.Empty

  def html(req: Request, resp: Response) = withAuth(req, resp) { auth =>
    // display push progress, once done, let user to process it
    <html>
      <head>
        {headerPart(req, auth)}
      </head>
      <body>
        {bodyPart(req, auth)}
      </body>
    </html>

  }

}