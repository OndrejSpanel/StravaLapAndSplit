package com.github.opengrabeso.mixtio
package frontend
package views.settings

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
)(implicit ec: ExecutionContext) extends Presenter[SettingsPageState.type] {

  // time changes once per 1000 ms, but we do not know when. If one would use 1000 ms, the error could be almost 1 sec if unlucky.
  // By using 200 ms we are sure the error will be under 200 ms
  dom.window.setInterval(() => model.subProp(_.currentTime).set(ZonedDateTime.now()), 200)

  model.subProp(_.settings.maxHR).listen(p => userContextService.api.foreach(_.settings.max_hr(p)))
  model.subProp(_.settings.elevFilter).listen(p => userContextService.api.foreach(_.settings.elev_filter(p)))
  model.subProp(_.settings.questTimeOffset).listen(p => userContextService.api.foreach(_.settings.quest_time_offset(p)))

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: SettingsPageState.type): Unit = {
  }

  def gotoSelect(): Unit = {
    application.goTo(SelectPageState)
  }
}
