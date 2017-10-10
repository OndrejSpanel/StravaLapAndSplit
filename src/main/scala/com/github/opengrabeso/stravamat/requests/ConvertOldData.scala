package com.github.opengrabeso.stravamat
package requests
import com.github.opengrabeso.stravamat.requests.Cleanup.BackgroundCleanup
import com.google.appengine.api.taskqueue.{DeferredTask, QueueFactory, TaskOptions}
import spark.{Request, Response}
import Main._

import scala.util.Try

/**
  * Convert data in old format - add metadata, rename ....
  * */
object ConvertOldData extends DefineRequest("/convert-old-data") {
  @SerialVersionUID(10L)
  case object BackgroundConvert extends DeferredTask {
    override def run(): Unit = {
      val files = Storage.enumerateAll()
      var converted = 0

      for {
        file <- files
        activityLoad <- Try(Storage.loadRawName[ActivityHeader](file))
        activity <- activityLoad
      } {
        val metadata = Seq("startTime" -> activity.id.startTime.toString)
        if (Storage.updateMetadata(file, metadata)) {
          converted += 1
        }
      }

      println(s"Convert: Total ${files.size} files, converted $converted")
    }
  }


  def html(request: Request, resp: Response) = {
    // add start time metadata to any activities missing them

    QueueFactory.getDefaultQueue add TaskOptions.Builder.withPayload(BackgroundConvert)

    <converted><deferred>Background request initiated</deferred></converted>
  }
}
