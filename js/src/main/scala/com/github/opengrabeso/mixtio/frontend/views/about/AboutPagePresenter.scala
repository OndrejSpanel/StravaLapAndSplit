package com.github.opengrabeso.mixtio.frontend
package views.about

import routing._

import io.udash._
import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class AboutPagePresenter(
  model: ModelProperty[AboutPageModel],
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[AboutPageState.type] {

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: AboutPageState.type): Unit = {
  }

  def gotoDummy(): Unit = {
    application.goTo(DummyPageState)
  }
}
