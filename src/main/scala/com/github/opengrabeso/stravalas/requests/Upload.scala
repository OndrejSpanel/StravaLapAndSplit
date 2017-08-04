package com.github.opengrabeso.stravalas
package requests

import java.io.{ByteArrayInputStream, InputStream}

import com.github.opengrabeso.stravalas.Main.NoActivity
import net.suunto3rdparty.Settings
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.IOUtils
import spark.{Request, Response}

object Upload extends DefineRequest.Post("/upload") with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val fif = new DiskFileItemFactory()
    val maxMB = 32
    fif.setSizeThreshold(maxMB * 1024 * 1024)

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext
      def next() = items.next
    }

    var timezone = Option.empty[String]
    itemsIterator.foreach { item =>
      if (!item.isFormField && item.getFieldName == "activities") {
        if (item.getName!="") {
          storeFromStream(auth.userId, item.getName, timezone.get, item.openStream())
        }
      } else if (item.isFormField && item.getFieldName == "timezone") {
        timezone = Some(IOUtils.toString(item.openStream(), "UTF-8"))
      }
    }

    resp.redirect("/selectActivity")
    Nil
  }


  def storeFromStreamWithDigest(userId: String, name: String, timezone: String, stream: InputStream, digest: String) = {
    import MoveslinkImport._
    def now() = System.currentTimeMillis()
    val start = now()
    def logTime(msg: String) = println(s"$msg: time ${now()-start}")

    val extension = name.split('.').last
    val actData: Seq[Main.ActivityEvents] = extension.toLowerCase match {
      case "fit" =>
        FitImport(name, stream).toSeq
      case "sml" =>
        loadSml(name, digest, stream).toSeq.flatMap(loadFromMove(name, digest, _))
      case "xml" =>
        val maxHR = Settings(userId).maxHR
        loadXml(name, digest, stream, maxHR).zipWithIndex.flatMap { case (act,index) =>
          // some activities (Quest) have more parts, each part needs a distinct name
          val nameWithIndex = if (index > 0) s"$name-$index" else name
          loadFromMove(nameWithIndex, digest, act)
        }
      case e =>
        Nil
    }
    logTime("Import file")
    if (actData.nonEmpty) {
      for (act <- actData) {
        Storage.store(Main.namespace.stage, act.id.id.filename, userId, act, "digest" -> digest)
      }
    } else {
      Storage.store(Main.namespace.stage, name, userId, NoActivity, "digest" -> digest)
    }
    logTime("Store file")
  }

  def storeFromStream(userId: String, name: String, timezone: String, streamOrig: InputStream) = {
    import MoveslinkImport._
    // we may read only once, we need to buffer it

    val fileBytes = IOUtils.toByteArray(streamOrig)
    val digest = Main.digest(fileBytes)

    val stream = new ByteArrayInputStream(fileBytes)

    storeFromStreamWithDigest(userId, name, timezone, stream, digest)
  }

}
