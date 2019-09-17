package com.github.opengrabeso.mixtio
package frontend
package views.dummy

import routing._

import io.udash._
import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class DummyPagePresenter(
  model: ModelProperty[DummyPageModel],
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[DummyPageState.type] {


  /** We don't need any initialization, so it's empty. */
  override def handleState(state: DummyPageState.type): Unit = {
  }

  def gotoAbout() = {
    application.goTo(AboutPageState)

  }
}
