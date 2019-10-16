package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import model._
import facade.UdashApp
import routing._
import io.udash._
import common.model._

import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  userContextService: services.UserContextService,
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[EditPageState] {

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: EditPageState): Unit = {
  }

  private def eventsToSend = {
    val events = model.subProp(_.events).get
    val eventsToSend = events.map { e =>
      if (!e.active) ("delete", e.time)
      else if (e.action == "lap") (e.action, e.time)
      else if (e.boundary) (e.action, e.time)
      else ("", e.time)
    }
    eventsToSend
  }

  def toggleSplitDisable(time: Int): Unit = {
    val events = model.subProp(_.events).get
    val from = events.dropWhile(_.time < time)
    for (first <- from.headOption) {
      val togglingOff = first.active
      val toggle = first +: (if (togglingOff) {
        from.drop(1).takeWhile(e => !e.boundary)
      } else {
        from.drop(1).takeWhile(e => !e.boundary && !e.active)
      })

      val toggleTimes = toggle.map(_.time).toSet
      val toggled = events.map { e =>
        if (toggleTimes contains e.time) e.copy(active = !e.active)
        else e
      }
      model.subProp(_.events).set(toggled)
    }
  }

  def download(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      userContextService.api.get.downloadEditedActivity(fileId, UdashApp.sessionId, eventsToSend, time)
    }
  }

  def sendToStrava(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      uploads.startUpload(userContextService.api.get, Seq(time))

      // uploads identified by the starting time
      // it could be much simpler, as we are starting one upload each time
      // however we share PendingUploads with the `select` which initiates multiple uploads at the same time
      object uploads extends PendingUploads[Int] {

        def sendToStrava(fileIds: Seq[Int]) = {
          userContextService.api.get.sendEditedActivityToStrava(fileId, UdashApp.sessionId, eventsToSend, time).map {
            _.toSeq.map(time -> _)
          }
        }

        def modifyActivities(fileId: Set[Int])(modify: EditEvent => EditEvent): Unit = {
          if (fileId.nonEmpty) model.subProp(_.events).set {
            model.subProp(_.events).get.map { e =>
              if (fileId contains e.time) {
                modify(e)
              } else e
            }
          }
        }

        def setStravaFile(fileId: Set[Int], stravaId: Option[FileId.StravaId]): Unit = {
          modifyActivities(fileId)(_.copy(strava = stravaId))
        }

        def setUploadProgressFile(fileId: Set[Int], uploading: Boolean, uploadState: String): Unit = {
          modifyActivities(fileId)(_.copy(uploading = uploading, uploadState = uploadState))
        }

      }
    }
  }
}
