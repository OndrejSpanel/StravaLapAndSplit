package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object Push extends DefineRequest("/push") with ActivityRequestHandler {
  def html(req: Request, resp: Response) = {
    val port = req.params("port").toInt
    // perform the OAuth login, once done, report to the push application local server
    <ok>OK</ok>
  }
}
