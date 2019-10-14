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

  def download(): Unit = ???

  def sendToStrava(): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      val events = model.subProp(_.events).get
      val eventsToSend = events.flatMap { e =>
        if (e.action == "lap") Some((false, e.action, e.time))
        else if (e.boundary) Some((e.processed, e.action, e.time))
        else None
      }
      userContextService.api.get.sendEditedActivitiesToStrava(fileId, UdashApp.sessionId, eventsToSend)
    }
  }
}
