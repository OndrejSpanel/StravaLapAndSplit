package com.github.opengrabeso.mixtio
package rest

import java.io.{ByteArrayInputStream, InputStream, PushbackInputStream}
import java.util.zip.GZIPInputStream

import PushRestAPIServer._

object PushRestAPIServer {
  private def decompressStream(input: InputStream): InputStream = {
    val pushbackInputStream = new PushbackInputStream(input, 2)
    val signature = new Array[Byte](2)
    pushbackInputStream.read(signature)
    pushbackInputStream.unread(signature)
    if (signature(0) == 0x1f.toByte && signature(1) == 0x8b.toByte) new GZIPInputStream(pushbackInputStream)
    else pushbackInputStream
  }
}

class PushRestAPIServer(parent: UserRestAPIServer, session: String, localTimeZone: String) extends PushRestAPI with RestAPIUtils {

  private def userId: String = parent.userAuth.id

  def offerFiles(files: Seq[(String, String)]) = syncResponse {
    val needed = files.filterNot { case (file, digest) =>
      Storage.check(Main.namespace.stage, userId, file, digest)
    }.map(_._1)


    @SerialVersionUID(10L)
    case object Anything
    println(s"offered ${files.size}, needed ${needed.size}")
    for (f <- needed) {
      println(s"offered $f for $userId")
      Storage.store(Main.namespace.pushProgress(session), f, userId, Anything, Anything)
    }
    // write started tag once the pending files information is complete, to mark it can be scanned now
    Storage.store(Main.namespace.pushProgress(session), "/started", userId, Anything, Anything)
    needed
  }

  // upload a single file
  def uploadFile(id: String, content: Array[Byte], digest: String) = syncResponse {
    // once stored, remove from the "needed" list
    val stream = new ByteArrayInputStream(content)
    val decompressed = decompressStream(stream)

    requests.Upload.storeFromStreamWithDigest(userId, id, localTimeZone, decompressed, digest)
    val pushNamespace = Main.namespace.pushProgress(session)
    Storage.delete(Storage.FullName(pushNamespace, id, userId))
    println(s"pushed $id for $userId")
  }

  def expected = syncResponse {
    val pushNamespace = Main.namespace.pushProgress(session)
    val started = Storage.enumerate(pushNamespace, userId, Some(f => f == "started")).nonEmpty
    if (!started) {
      Seq("") // special response - not empty, but not the list of the files yes
    } else {
      val pending = for (f <- Storage.enumerate(pushNamespace, userId)) yield f._2
      for (f <- pending) {
        Storage.delete(Storage.FullName(pushNamespace, f, userId))
      }
      if (pending.isEmpty) {
        // once we return empty response, we can delete the "started" marker file
        Storage.delete(Storage.FullName(pushNamespace, "started", userId))
      }
      pending.toSeq
    }
  }


}
