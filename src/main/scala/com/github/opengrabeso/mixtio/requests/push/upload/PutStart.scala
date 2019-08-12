package com.github.opengrabeso.mixtio
package requests
package push
package upload

import spark.{Request, Response}

object PutStart extends DefineRequest.Post("/push-put-start") {

  override def html(request: Request, resp: Response) = {
    val userId = request.queryParams("user")
    val totalFiles = request.queryParams("total-files").toInt
    val sessionId = request.cookie("sessionid")

    startProgress(userId, sessionId, totalFiles)

    Nil
  }


}
