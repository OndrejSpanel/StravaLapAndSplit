package com.github.opengrabeso.mixtio

import com.avsystem.commons.Try
import com.garmin.fit._

import scala.collection.JavaConverters._
import java.io.{File, _}
import com.opencsv.CSVReader

import scala.annotation.tailrec
import scala.util.Failure
import scala.util.chaining._

object FitConvert {
  import java.io.FileInputStream
  import java.io.FileOutputStream
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

  def loadFile(file: File) = {

    if (file.getName.endsWith(".fit.gz")) {

      val is = new FileInputStream(file)
      // for some reason it seems GZIPInputStream does not work with FitImport directly - we decompress it in memory instead
      val gzStream = decompressGzip(new GZIPInputStream(is))

      val ret = Try {
        FitImport(file.toString, "", gzStream).get
      }
      gzStream.close()
      is.close()

      ret
    } else if (file.getName.endsWith(".fit")) {
      val is = new FileInputStream(file)
      val ret = Try {
        FitImport(file.toString, "", is).get
      }
      is.close()
      ret
    } else Failure(new UnsupportedOperationException("Unsupported file format"))
  }

  def changeExtension(file: File, extension: String): File = {
    assert(extension.startsWith("."))
    // note: this fails when a directory contains a dot
    val baseName = file.toString.takeWhile(_ != '.')
    new File(baseName + extension)
  }

  def saveFile(outFile: File, activity: ActivityEvents): Unit = {
    val bytes = FitExport.export(activity)
    val outStream = new FileOutputStream(outFile)
    outStream.write(bytes)
    outStream.close()

    outFile.setLastModified(activity.id.startTime.toInstant.toEpochMilli)
  }

  def convertOneFile(inFile: File, outFile: File): Unit = {
    for (activity <- loadFile(inFile)) {
      saveFile(outFile, activity)
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

  private def convertSingleCSVLine(inFile: File, outFile: File, headerIndex: Map[String, Int], values: Array[String]) = {
    def safeValue(i: Int) = if (i >= 0 && i < values.length) Some(values(i)) else None
    def getColumn(x: String) = headerIndex.get(x).flatMap(safeValue)
    for (aFile <- getColumn("Filename")) {
      val aFileName = inFile.toPath.resolveSibling(aFile)
      for (a <- loadFile(aFileName.toFile)) {
        a.pipe { activity =>
          val sportName = getColumn("Activity Type").flatMap { s =>
            val condensedName = s.replaceAll("\\s", "")
            Try(common.model.SportId.withName(condensedName)).toOption
          }
          sportName.map { sport =>
            activity.copy(id = activity.id.copy(sportName = sport))
          }.getOrElse {
            activity
          }
        }.pipe { activity =>
          val outFileName = changeExtension(outFile.toPath.resolve(new File(aFile).toPath.getFileName).toFile, ".fit")

          saveFile(outFileName, activity)
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
        val headerIndex = headers.zipWithIndex.toMap
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
          val outName = new File(out).toPath.resolve(shortName).toFile
          if (dump) {
            dumpOneFile(f, changeExtension(outName, ".txt"))
          } else {
            convertOneFile(f, changeExtension(outName, ".fit"))
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
