package com.github.opengrabeso.stravalas
package requests
package push
package upload

import spark.{Request, Response}

object PutFile extends DefineRequest.Post("/push-put") {

  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val path = request.queryParams("path")

    val fileContent = request.raw().getInputStream

    println(s"Received content for $path")

    Upload.storeFromStream(auth, path, fileContent)

    resp.status(200)

    Nil
  }


}
