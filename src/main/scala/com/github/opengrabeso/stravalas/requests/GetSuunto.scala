package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}
import RequestUtils._

object GetSuunto extends DefineRequest("/getSuunto", method = Method.Get) with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {
    // make a request to a local web server (should be running on 8088)

    val localUri = "http://localhost:8088/"
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val requestEnum = buildGetRequest(localUri + "enum")

    try {
      val responseXML = requestEnum.execute().getContent
    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        throw ex
    }

    // TODO: ask for files one by one
    // TODO: some reporting back, to prevent timeout, and give user a feedback

    resp.redirect("/selectActivity")
    Nil
  }
}
