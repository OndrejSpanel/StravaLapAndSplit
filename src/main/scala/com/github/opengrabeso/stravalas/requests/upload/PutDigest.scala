package com.github.opengrabeso.stravalas
package requests
package upload

import spark.{Request, Response}

object PutDigest extends DefineRequest.Post("/putDigest") {

  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val path = request.queryParams("path")

    val digest = request.body()

    println(s"Received digest for $path: $digest")
    // check if such file / digest is already known and report back
    if (Storage.check(auth.userId, path, digest)) {
      resp.status(204) // already present
    } else {
      resp.status(200) // not present / not matching - send full file
    }

    Nil
  }


}
