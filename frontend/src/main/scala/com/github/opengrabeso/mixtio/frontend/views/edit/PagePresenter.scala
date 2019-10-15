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
    val eventsToSend = events.flatMap { e =>
      if (e.action == "lap") Some((e.action, e.time))
      else if (e.boundary) Some((e.action, e.time))
      else None
    }
    eventsToSend
  }

  def download(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      userContextService.api.get.downloadEditedActivity(fileId, UdashApp.sessionId, eventsToSend, time)
    }
  }

  def delete(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      ???
      //userContextService.api.get.downloadEditedActivity(fileId, UdashApp.sessionId, eventsToSend, time)
    }
  }

  def sendToStrava(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      userContextService.api.get.sendEditedActivityToStrava(fileId, UdashApp.sessionId, eventsToSend, time)
      // TODO: show progress / result
    }
  }
}
