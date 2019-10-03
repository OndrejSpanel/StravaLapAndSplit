package com.github.opengrabeso.mixtio
package frontend
package views.select

import routing._

import io.udash._
import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[SelectPageState.type] {

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: SelectPageState.type): Unit = {
  }

  def gotoDummy(): Unit = {
    application.goTo(DummyPageState)
  }
  def gotoSettings(): Unit = {
    application.goTo(SettingsPageState)
  }
}
