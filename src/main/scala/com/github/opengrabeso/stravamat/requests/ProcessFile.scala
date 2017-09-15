package com.github.opengrabeso.stravamat
package requests

import java.io.InputStream

import org.apache.commons.io.IOUtils
import spark.{Request, Response}

import scala.collection.mutable.ArrayBuffer

abstract class ProcessFile(value: String) extends DefineRequest.Post(value) with ParseFormDataGen[Int] {

  def inputName = "process_time"
  def parse(v: String) = v.toInt

  class ProcessFileParams {
    var id = ""
    val events =  ArrayBuffer.empty[String]
  }

  type Context = ProcessFileParams

  def createContext = new ProcessFileParams


  override def readItem(ctx: Context, itemName: String, stream: InputStream): Unit = {
    def readString = IOUtils.toString(stream, "UTF-8")
    itemName match {
      case "events" =>
        ctx.events += readString
      case "id" =>
        ctx.id = readString
      case _ =>

    }
  }


  def process(req: Request, resp: Response, export: Array[Byte], filename: String): Unit

  override def html(req: Request, resp: Response) = withAuth(req, resp) { auth =>

    val (splits, pars) = activities(req)
    val id = pars.id

    for (splitTime <- splits) {

      val eventsInput = pars.events

      for (events <- Storage.load2nd[Main.ActivityEvents](Main.namespace.edit, id, auth.userId)) {

        val adjusted = Main.adjustEvents(events, eventsInput)

        val split = adjusted.split(splitTime)

        split.foreach { save =>

          val export = FitExport.export(save)

          process(req, resp, export, s"attachment;filename=split_${id}_$splitTime.fit")
        }
      }
    }

    Nil

  }
}
