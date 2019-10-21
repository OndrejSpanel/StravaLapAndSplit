package com.github.opengrabeso.mixtio
package requests

import java.io.{ByteArrayInputStream, InputStream, ObjectInputStream}
import java.time.ZoneId

import Main.NoActivity
import shared.Timing
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.IOUtils
import spark.{Request, Response}

import scala.util.Try

object Upload extends ActivityStorage {
  def storeFromStreamWithDigest(userId: String, name: String, timezone: String, stream: InputStream, digest: String): Seq[Main.ActivityEvents] = {
    import MoveslinkImport._
    val timing = Timing.start()

    val extension = name.split('.').last
    val actData: Seq[Main.ActivityEvents] = extension.toLowerCase match {
      case "fit" =>
        FitImport(name, stream).toSeq
      case "sml" =>
        loadSml(name, digest, stream).toSeq
      case "xml" =>
        loadXml(name, digest, stream, timezone).zipWithIndex.flatMap { case (act,index) =>
          // some activities (Quest) have more parts, each part needs a distinct name
          val nameWithIndex = if (index > 0) s"$name-$index" else name
          loadFromMove(nameWithIndex, digest, act)
        }
      case _ =>
        // unknown extension, try deserialization, might be a file extracted from the server Cloud Storage
        Try {
          val ois = new ObjectInputStream(stream)
          val _ = ois.readObject()
          val obj = ois.readObject()
          obj match {
            case act: Main.ActivityEvents =>
              act
          }
        }.toOption.toSeq
    }
    timing.logTime("Import file")
    val ret = if (actData.nonEmpty) {
      for (act <- actData) yield {
        val actOpt = act.cleanPositionErrors // .optimize
        storeActivity(Main.namespace.stage, actOpt, userId)
        actOpt
      }
    } else {
      Storage.store(Main.namespace.stage, name, userId, NoActivity, NoActivity, Seq("digest" -> digest))
      Nil
    }
    timing.logTime("Store file")
    ret
  }

  def storeFromStream(userId: String, name: String, timezone: String, streamOrig: InputStream): Seq[Main.ActivityEvents] = {
    val fileBytes = IOUtils.toByteArray(streamOrig)
    val digest = Main.digest(fileBytes)

    val stream = new ByteArrayInputStream(fileBytes)

    storeFromStreamWithDigest(userId, name, timezone, stream, digest)
  }

}
