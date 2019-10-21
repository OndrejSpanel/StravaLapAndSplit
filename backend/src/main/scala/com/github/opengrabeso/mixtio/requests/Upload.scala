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

object Upload extends DefineRequest.Post("/upload") with ActivityStorage {
  override def html(request: Request, resp: Response) = {
    withAuth(request, resp) { auth =>

      val fif = new DiskFileItemFactory()
      val maxMB = 32
      fif.setSizeThreshold(maxMB * 1024 * 1024)

      val upload = new ServletFileUpload(fif)

      val items = upload.getItemIterator(request.raw)

      val itemsIterator = new Iterator[FileItemStream] {
        def hasNext = items.hasNext

        def next() = items.next
      }

      // TODO: obtain client timezone - neeeded when uploading Quest XML files
      val timezone = ZoneId.systemDefault().toString
      itemsIterator.foreach { item =>
        if (!item.isFormField && item.getFieldName == "files") {
          if (item.getName != "") {
            storeFromStream(auth.userId, item.getName, timezone, item.openStream())
          }
        }
      }
      resp.status(200)
      Nil
    }
  }


  def storeFromStreamWithDigest(userId: String, name: String, timezone: String, stream: InputStream, digest: String) = {
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
    if (actData.nonEmpty) {
      for (act <- actData) {
        val actOpt = act.cleanPositionErrors // .optimize
        storeActivity(Main.namespace.stage, actOpt, userId)
      }
    } else {
      Storage.store(Main.namespace.stage, name, userId, NoActivity, NoActivity, Seq("digest" -> digest))
    }
    timing.logTime("Store file")
  }

  def storeFromStream(userId: String, name: String, timezone: String, streamOrig: InputStream) = {
    val fileBytes = IOUtils.toByteArray(streamOrig)
    val digest = Main.digest(fileBytes)

    val stream = new ByteArrayInputStream(fileBytes)

    storeFromStreamWithDigest(userId, name, timezone, stream, digest)
  }

}
