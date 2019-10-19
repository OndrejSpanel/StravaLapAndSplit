package com.github.opengrabeso.mixtio
package frontend
package views
package push

import java.time.ZonedDateTime

import routing.{PushPageState, RoutingState}
import io.udash._
import org.scalajs.dom

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
  sessionId: String
) extends ViewFactory[PushPageState] with settings_base.SettingsFactory with PageFactoryUtils {
  import scala.concurrent.ExecutionContext.Implicits.global

  private def updatePending(model: ModelProperty[PageModel]): Unit = {
    for (pending <- userService.api.get.push(sessionId, "").expected) {
      if (pending != Seq("")) {
        model.subProp(_.pending).set(pending)
      }
      if (pending.nonEmpty) {
        dom.window.setTimeout(() => updatePending(model), 500) // TODO: once long-poll is implemented, reduce or remove the delay
      }
    }
  }

  override def create(): (View, Presenter[PushPageState]) = {
    val model = ModelProperty(PageModel(settings_base.SettingsModel(), Seq(""))) // start with non-empty placeholder until real state is confirmed

    loadSettings(model.subModel(_.s), userService)

    updatePending(model)

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}