package com.github.opengrabeso.stravamat
package requests
package push
package upload

import java.io.InputStream
import java.util.zip.GZIPInputStream

import spark.{Request, Response}

object PutFile extends DefineRequest.Post("/push-put") {

  override def html(request: Request, resp: Response) = {
    val path = request.queryParams("path")
    val userId = request.queryParams("user")
    val timezone = request.queryParams("timezone")

    // we expect to receive digest separately, as this allows us to use the stream incrementally while parsing XML
    // - note: client has already computed any it because it verified it before sending data to us
    val digest = request.queryParams("digest")

    val encoding = request.headers("Content-Encoding")

    println(s"Put file $path")
    println(s"  Digest $digest}")
    println(s"  Encoding ${Option(encoding).getOrElse("null")}")

    val sessionId = request.cookie("sessionid")

    val rawInputStream = request.raw().getInputStream
    // it seems production App Engine already decodes gziped request body, but development one does not
    val fileContent = rawInputStream

    val logProgress = false
    val input = if (logProgress) {
      object WrapStream extends InputStream {
        var progress = 0
        var reported = 0
        val reportEach = 100000

        def report(chars: Int): Unit = {
          progress += chars
          if (progress > reported + reportEach) {
            reported = progress / reportEach * reportEach
            println(s" read $reported B")
          }
        }

        def read() = {
          report(1)
          fileContent.read()
        }

        override def read(b: Array[Byte], off: Int, len: Int) = {
          val read = fileContent.read(b, off, len)
          report(read)
          read
        }
      }
      WrapStream
    } else {
      fileContent
    }

    println(s"Received content for $path")

    Upload.storeFromStreamWithDigest(userId, path, timezone, input, digest)

    reportProgress(userId, sessionId)

    resp.status(200)

    Nil
  }


}
