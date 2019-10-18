package com.github.opengrabeso.mixtio
package frontend
package views.push

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
) extends ViewFactory[PushPageState] {
  import scala.concurrent.ExecutionContext.Implicits.global

  private def updatePending(model: ModelProperty[PageModel]) = {
    for (pending <- userService.api.get.push(sessionId, "").expected) {
      if (pending != Seq("")) {
        model.subProp(_.pending).set(pending)
      }
      if (pending.nonEmpty) {
        dom.window.setTimeout(() => model.subProp(_.currentTime).set(ZonedDateTime.now()), 500) // TODO: once long-poll is implemented, reduce or remove the delay
      }
    }
  }

  override def create(): (View, Presenter[PushPageState]) = {
    val model = ModelProperty(PageModel(true, SettingsStorage(), ZonedDateTime.now(), Seq()))

    class NumericRangeValidator(from: Int, to: Int) extends Validator[Int] {
      def apply(value: Int) = Future.successful{
        if (value >= from && value <= to) Valid
        else Invalid(DefaultValidationError(s"Expected value between $from and $to"))
      }
    }

    model.subProp(_.settings.questTimeOffset).addValidator(new NumericRangeValidator(-120, +120))
    model.subProp(_.settings.maxHR).addValidator(new NumericRangeValidator(90, 240))

    for (userSettings <- userService.api.get.allSettings) {
      model.subProp(_.settings).set(userSettings)
      model.subProp(_.loading).set(false)
    }
    updatePending(model)

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}