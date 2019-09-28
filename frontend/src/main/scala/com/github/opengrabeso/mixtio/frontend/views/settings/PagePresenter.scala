package com.github.opengrabeso.mixtio
package frontend
package views.settings

import routing._
import io.udash._

import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  userContextService: services.UserContextService,
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[SettingsPageState.type] {


  model.subProp(_.settings.maxHR).listen(p => userContextService.api.foreach(_.settings.max_hr(p)))
  model.subProp(_.settings.elevFilter).listen(p => userContextService.api.foreach(_.settings.elev_filter(p)))
  model.subProp(_.settings.questTimeOffset).listen(p => userContextService.api.foreach(_.settings.quest_time_offset(p)))

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: SettingsPageState.type): Unit = {
  }

  def gotoAbout(): Unit = {
    application.goTo(AboutPageState)
  }
}
