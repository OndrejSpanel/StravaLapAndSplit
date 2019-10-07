package com.github.opengrabeso.mixtio
package frontend
package views.edit

import java.time.ZonedDateTime

import routing._
import io.udash._

import scala.concurrent.ExecutionContext
import org.scalajs.dom

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  userContextService: services.UserContextService,
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[EditPageState] {

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: EditPageState): Unit = {
  }

  def gotoSelect(): Unit = {
    application.goTo(SelectPageState)
  }
}
