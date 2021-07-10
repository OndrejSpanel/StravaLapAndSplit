package com.github.opengrabeso.mixtio

import com.garmin.fit._

import scala.collection.JavaConverters._
import java.io.{BufferedWriter, File, FileInputStream, FileOutputStream, OutputStreamWriter, PrintWriter, Writer}

object FitConvert {
  def convertOneFile(inFile: File, outFile: File): Unit = {
    val stream = new FileInputStream(inFile)

    // we do not care about digests here - we just handle fit files, do not store them on mixtio
    val activity = FitImport(inFile.getName, "", stream).get
    stream.close()
    val bytes = FitExport.export(activity)
    val outStream = new FileOutputStream(outFile)
    outStream.write(bytes)
    outStream.close()
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
          outWriter.println("  " + field.getName)
        }
      }
    }

    decode.read(in, listener)
    outWriter.close()
    writer.close()
    outStream.close()

  }

  def main(args: Array[String]): Unit = {
    val (options, files) = args.partition(_.startsWith("-"))
    val dump = options.contains("-dump")
    if (files.length == 2) {
      val in = files(0)
      val out = files(1)
      val inFile = new File(in)
      if (inFile.exists && inFile.isDirectory) {
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
