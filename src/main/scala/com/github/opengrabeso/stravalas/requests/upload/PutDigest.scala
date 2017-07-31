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

    // check if such file / digest is already known and report back
    if (Storage.check(auth.userId, path, digest)) {
      println(s"Received matching digest for $path")
      resp.status(204) // already present
    } else {
      println(s"Received non-matching digest for $path")

      // debugging opportunity
      Storage.check(auth.userId, path, digest)

      resp.status(200) // not present / not matching - send full file
    }

    Nil
  }


}
