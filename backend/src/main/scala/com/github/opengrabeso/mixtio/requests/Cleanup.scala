package com.github.opengrabeso.mixtio
package requests
import com.google.appengine.api.taskqueue.{DeferredTask, QueueFactory, TaskOptions}
import spark.{Request, Response}
import java.time.ZonedDateTime

/**
  * Regular cleanup performed periodically, for all users, not requiring user access information
  * */
object Cleanup extends DefineRequest("/cleanup") {

  @SerialVersionUID(10L)
  case object BackgroundCleanup extends DeferredTask {
    override def run(): Unit = {
      val cleanedCloudStorage = Storage.cleanup()
      val cleanedDataStore = DStorage.cleanup()
      println(s"Cleaned $cleanedCloudStorage storage items, $cleanedDataStore datastore items")
    }
  }


  def html(request: Request, resp: Response) = {
    val periodic = request.queryParams("periodic")
    if (periodic != null) {

      QueueFactory.getDefaultQueue add TaskOptions.Builder.withPayload(BackgroundCleanup)

      <cleaned><deferred>Background request initiated</deferred></cleaned>
    } else {
      println("Unknown cleanup type")
      resp.status(400) // Bad Request
      <cleaned><error>Syntax error</error></cleaned>
    }
  }
}
