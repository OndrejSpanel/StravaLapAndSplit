package com.github.opengrabeso.mixtio

import java.io.{File, FileInputStream, FileOutputStream}
import scala.collection.JavaConverters._

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

  def main(args: Array[String]): Unit = {
    if (args.length == 2) {
      val in = args(0)
      val out = args(1)
      val inFile = new File(in)
      if (inFile.exists && inFile.isDirectory) {
        new File(out).mkdirs()

        for (f <- inFile.listFiles) {
          val shortName = f.getName
          val outName = new File(out).toPath.resolve(shortName)
          convertOneFile(f, outName.toFile)
        }
      } else {
        convertOneFile(inFile, new File(out))
      }
    }
  }
}
