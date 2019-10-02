package com.github.opengrabeso.mixtio
package requests
package push
package upload

import java.io.{FilterInputStream, InputStream, PushbackInputStream}
import java.util.zip.GZIPInputStream

import common.Util._
import spark.{Request, Response}

object PutFile extends DefineRequest.Post("/push-put") {


  /** from https://blog.art-of-coding.eu/compressed-http-requests/
    * Check if InputStream is gzip'ed by looking at the first two bytes (magic number)
    * and if it is, return a GZIPInputStream wrapped stream.
    *
    * @param input An input stream.
    * @return The input or GZIIPInputStream(input).
    */
  private def decompressStream(input: InputStream): InputStream = {
    val pushbackInputStream = new PushbackInputStream(input, 2)
    val signature = new Array[Byte](2)
    pushbackInputStream.read(signature)
    pushbackInputStream.unread(signature)
    if (signature(0) == 0x1f.toByte && signature(1) == 0x8b.toByte) new GZIPInputStream(pushbackInputStream)
    else pushbackInputStream
  }

  override def html(request: Request, resp: Response) = {
    val path = request.queryParams("path")
    val userId = request.queryParams("user")
    val timezone = request.queryParams("timezone")

    // we expect to receive digest separately, as this allows us to use the stream incrementally while parsing XML
    // - note: client has already computed any it because it verified it before sending data to us
    val digest = request.queryParams("digest")
    val contentLength = Option(request.headers("Content-Length"))

    //val encoding = request.headers("Content-Encoding")

    println(s"Put file $path, Digest $digest}")
    //println(s"  Encoding ${Option(encoding).getOrElse("null")}")
    for (cl <- contentLength) {
      println(s"  Size ${cl.toInt.toByteSize}")
    }

    val sessionId = request.cookie("sessionid")

    // note: production App Engine already decodes gziped request body, but development one does not

    val rawInputStream = request.raw().getInputStream
    val fileContent = decompressStream(rawInputStream)


    val logProgress = false
    val input = if (logProgress) {
      object WrapStream extends InputStream {
        var progress = 0
        var reported = 0
        val reportEach = 100000

        def report(chars: Int) = {
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


    Upload.storeFromStreamWithDigest(userId, path, timezone, input, digest)

    reportProgress(userId, sessionId)

    resp.status(200)

    Nil
  }


}
