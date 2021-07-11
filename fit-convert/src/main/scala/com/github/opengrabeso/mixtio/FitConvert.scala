package com.github.opengrabeso.mixtio

import com.avsystem.commons.Try
import com.garmin.fit._

import scala.collection.JavaConverters._
import java.io.{File, _}
import com.opencsv.CSVReader

import java.util.zip.GZIPInputStream
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.chaining._

object FitConvert {
  def convertOneFile(inFile: File, outFile: File): Unit = {
    if (inFile.getName.endsWith(".fit")) {
      val stream = new FileInputStream(inFile)
      // we do not care about digests here - we just handle fit files, do not store them on mixtio
      val a = Try {
        FitImport(inFile.getName, "", stream).get
      }
      stream.close()
      for (activity <- a) {
        val bytes = FitExport.export(activity)
        val outStream = new FileOutputStream(outFile)
        outStream.write(bytes)
        outStream.close()
      }
    }
  }

  def dumpOneFile(inFile: File, outFile: File): Unit = {
    val in = new FileInputStream(inFile)

    val outStream = new FileOutputStream(outFile)
    val writer = new OutputStreamWriter(outStream, "UTF-8")
    val outWriter = new PrintWriter(writer)
    val decode = new Decode

    val listener = new MesgListener {
      override def onMesg(mesg: Mesg): Unit = {
        outWriter.println(mesg.getName)
        val fields = mesg.getFields.asScala
        for (field <- fields) {
          import Fit._
          val times = Set("time_created", "timestamp")
          val fieldValue = if (times.contains(field.getName)) {
            val garminTimestamp = field.getLongValue
            FitImport.fromTimestamp(garminTimestamp).toString
          } else {
            field.getType match {
              case BASE_TYPE_UINT32 | BASE_TYPE_SINT32 | BASE_TYPE_UINT16 | BASE_TYPE_SINT16 | BASE_TYPE_UINT8 | BASE_TYPE_SINT8 =>
                field.getLongValue.toString
              case BASE_TYPE_ENUM =>
                s"enum ${field.getLongValue}"
              case x =>
                f"? (type $x%X)"

            }
          }
          outWriter.println(s"  ${field.getName} = $fieldValue")
        }
      }
    }

    decode.read(in, listener)
    outWriter.close()
    writer.close()
    outStream.close()

  }

  import java.io.FileInputStream
  import java.io.FileOutputStream
  import java.io.IOException
  import java.util.zip.GZIPInputStream

  def decompressGzip(source: GZIPInputStream): InputStream = {
    try {
      val os = new ByteArrayOutputStream
      try { // copy GZIPInputStream to FileOutputStream
        val buffer = new Array[Byte](1024)
        var len = 0
        do {
          len = source.read(buffer)
          if (len > 0) os.write(buffer, 0, len)
        } while (len > 0)
      } finally {
        os.close()
      }
      new ByteArrayInputStream(os.toByteArray)
    }
  }

  private def convertSingleCSVLine(inFile: File, outFile: File, headerIndex: Map[String, Int], values: Array[String]) = {
    def safeValue(i: Int) = if (i >=0 && i < values.length) Some(values(i)) else None
    def getColumn(x: String) = headerIndex.get(x).flatMap(safeValue)
    for (aFile <- getColumn("Filename")) {
      if (aFile.endsWith(".fit.gz")) {
        val aFileName = inFile.toPath.resolveSibling(aFile)

        val is = new FileInputStream(aFileName.toFile)
        // for some reason it seems GZIPInputStream does not work with FitImport directly - we decompress it in memory instead
        val gzStream = decompressGzip(new GZIPInputStream(is))

        val a = Try {
          FitImport(aFileName.toString, "", gzStream).get.pipe { activity =>
            getColumn("Activity Name") match {
              case Some(name) =>
                activity.copy(id = activity.id.copy(name = name))
              case None =>
                activity
            }
          }.pipe { activity =>
            val sportName = getColumn("Activity Type").flatMap { s =>
              val condensedName = s.replaceAll("\\s", "")
              Try(common.model.SportId.withName(condensedName)).toOption
            }
            sportName.map { sport =>
              activity.copy(id = activity.id.copy(sportName = sport))
            }.getOrElse {
              activity
            }
          }
        }

        gzStream.close()
        is.close()

        for (activity <- a) {

          val outFileName = outFile.toPath.resolve(new File(aFile).toPath.getFileName).toString

          val baseName = outFileName.takeWhile(_ != '.')

          val bytes = FitExport.export(activity)
          val outStream = new FileOutputStream(baseName + ".fit")
          outStream.write(bytes)
          outStream.close()

          new File(baseName + ".fit").setLastModified(activity.id.startTime.toInstant.toEpochMilli)

        }
      }
    }
  }


  def main(args: Array[String]): Unit = {
    val (options, files) = args.partition(_.startsWith("-"))
    val dump = options.contains("-dump")
    val strava = options.contains("-strava")
    if (files.length == 2) {
      val in = files(0)
      val out = files(1)
      val inFile = new File(in)
      if (inFile.exists && strava) {
        // Strava specific activities archive (based on activities.csv) handling
        new File(out).mkdirs()
        val csvReader = new CSVReader(new FileReader(in))
        val headers = csvReader.readNextSilently()
        val headerIndex = (headers.zipWithIndex).toMap
        @tailrec
        def processNextLine(): Unit = {
          val values = csvReader.readNext()
          if (values != null) {
            convertSingleCSVLine(inFile, new File(out), headerIndex, values)
            processNextLine()
          }
        }
        processNextLine()
      } else if (inFile.exists && inFile.isDirectory) {
        // batch processing a directory
        new File(out).mkdirs()

        for (f <- inFile.listFiles) {
          val shortName = f.getName
          val outName = new File(out).toPath.resolve(shortName)
          if (dump) {
            val outName = new File(out).toPath.resolve(shortName)
            dumpOneFile(f, new File(outName.toString + ".txt"))
          } else {
            convertOneFile(f, outName.toFile)
          }
        }
      } else {
        if (dump) {
          dumpOneFile(inFile, new File(out))
        } else {
          convertOneFile(inFile, new File(out))
        }
      }
    }
  }
}
