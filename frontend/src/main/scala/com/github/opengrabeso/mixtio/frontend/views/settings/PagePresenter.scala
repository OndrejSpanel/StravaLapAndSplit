package com.github.opengrabeso.mixtio
package frontend
package views.settings

import routing._
import io.udash._

import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[SettingsPageState.type] {

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: SettingsPageState.type): Unit = {
  }

  def gotoAbout(): Unit = {
    application.goTo(AboutPageState)
  }
}
