package com.github.opengrabeso.stravalas
package requests
package push
package upload

import spark.{Request, Response}

object PutDigest extends DefineRequest.Post("/push-put-digest") {

  override def html(request: Request, resp: Response) = {
    val path = request.queryParams("path")
    val userId = request.queryParams("user")
    val totalFiles = request.queryParams("total-files").toInt
    val doneFiles = request.queryParams("done-files").toInt
    val sessionId = request.cookie("sessionid")

    val digest = request.body()

    if (false) { // for debugging sync progress UI
      Thread.sleep(1000)
    }

    // check if such file / digest is already known and report back
    if (Storage.check(Main.namespace.stage, userId, path, digest)) {
      println(s"Received matching digest for $path")
      resp.status(204) // status No content: already present

      saveProgress(userId, sessionId, totalFiles, doneFiles)

    } else {
      println(s"Received non-matching digest for $path")

      // debugging opportunity
      //Storage.check(Main.namespace.stage, userId, path, digest)

      resp.status(200) // status OK: not matching - send full file
    }

    Nil
  }


}
