package com.github.opengrabeso.mixtio
package requests

import java.io.{ByteArrayInputStream, InputStream}
import java.util.zip.ZipEntry

import org.apache.commons.io.IOUtils
import spark.{Request, Response}

import scala.xml.NodeSeq

object Download extends ProcessFile("/download") {
  def processAll(split: Seq[(Int, Main.ActivityEvents)], id: String)(req: Request, resp: Response): NodeSeq = {

    val out = resp.raw.getOutputStream

    // zip multiple files

    import java.io.BufferedOutputStream
    import java.util.zip.ZipOutputStream

    if (split.length > 1 ) {
      val short = id.lastIndexOf("/")

      val shortFilename = if (short < 0) id else id.drop(short + 1)

      val filename = s"$shortFilename.zip"
      val contentType = "application/octet-stream"
      resp.status(200)
      resp.header("Content-Disposition", filename)
      resp.`type`(contentType)

      val os = new BufferedOutputStream(out)
      val outZip = new ZipOutputStream(os)
      try {
        for ((splitTime, splitFile) <- split) {
          val filename = s"${shortFilename}_$splitTime.fit"
          val export = FitExport.export(splitFile)
          val fi = new ByteArrayInputStream(export)
          val entry = new ZipEntry(filename)
          outZip.putNextEntry(entry)
          IOUtils.copy(fi, outZip)
        }
      } finally {
        outZip.close()
        os.close()
      }


    } else if (split.nonEmpty) {
      val splitTime = split.head._1
      val export = FitExport.export(split.head._2)
      val filename = s"split_${id}_$splitTime.fit"

      val contentType = "application/octet-stream"
      resp.status(200)
      resp.header("Content-Disposition", filename)
      resp.`type`(contentType)
      out.write(export)
    }

    Nil
  }

  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit = {


  }


}
