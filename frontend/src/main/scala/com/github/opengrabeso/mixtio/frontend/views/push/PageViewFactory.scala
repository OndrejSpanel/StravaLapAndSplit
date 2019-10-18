package com.github.opengrabeso.mixtio
package frontend
package views.push

import java.time.ZonedDateTime

import routing.{RoutingState, PushPageState}
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
) extends ViewFactory[PushPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[PushPageState.type]) = {
    val model = ModelProperty(PageModel(true, SettingsStorage(), ZonedDateTime.now()))

    class NumericRangeValidator(from: Int, to: Int) extends Validator[Int] {
      def apply(value: Int) = Future.successful{
        if (value >= from && value <= to) Valid
        else Invalid(DefaultValidationError(s"Expected value between $from and $to"))
      }
    }

    model.subProp(_.settings.questTimeOffset).addValidator(new NumericRangeValidator(-120, +120))
    model.subProp(_.settings.maxHR).addValidator(new NumericRangeValidator(90, 240))

    for {
      userAPI <- userService.api
      userSettings <- userAPI.allSettings
    } {
      model.subProp(_.settings).set(userSettings)
      model.subProp(_.loading).set(false)
    }

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}