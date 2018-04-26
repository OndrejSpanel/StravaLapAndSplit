package com.github.opengrabeso.stravimat
package requests

import java.io.InputStream

import org.apache.commons.io.IOUtils
import spark.{Request, Response}

import scala.collection.mutable.ArrayBuffer
import scala.xml.NodeSeq

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


  def processAll(split: Seq[(Int, Main.ActivityEvents)], id: String)(req: Request, resp: Response): NodeSeq

  override def html(req: Request, resp: Response) = withAuth(req, resp) { auth =>

    val (splits, pars) = activities(req)
    val id = pars.id

    val splitFragments = for {
      splitTime <- splits
      events <- Storage.load2nd[Main.ActivityEvents](Storage.getFullName(Main.namespace.edit, id, auth.userId))
      adjusted = Main.adjustEvents(events, pars.events)
      split <- adjusted.split(splitTime)
    } yield {
      splitTime -> split
    }
    processAll(splitFragments, id)(req, resp)


  }
}
