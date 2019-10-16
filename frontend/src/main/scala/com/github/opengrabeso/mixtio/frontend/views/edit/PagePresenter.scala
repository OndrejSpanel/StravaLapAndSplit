package com.github.opengrabeso.mixtio
package frontend
package views.edit

import facade.UdashApp
import routing._
import io.udash._

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
      userContextService.api.get.sendEditedActivityToStrava(fileId, UdashApp.sessionId, eventsToSend, time)
      // TODO: show progress / result
    }
  }
}
