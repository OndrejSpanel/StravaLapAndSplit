package com.github.opengrabeso.mixtio
package frontend
package views.dummy

import routing._
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class DummyPageViewFactory(
  application: Application[RoutingState],
) extends ViewFactory[DummyPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[DummyPageState.type]) = {
    // Main model of the view
    val model = ModelProperty(
      DummyPageModel("dummy me")
    )

    val presenter = new DummyPagePresenter(model, application)
    val view = new DummyPageView(model, presenter)
    (view, presenter)
  }

  private object NonEmptyStringValidator extends Validator[String] {
    override def apply(element: String): Future[ValidationResult] = Future.successful {
      if (element.nonEmpty) Valid else Invalid("") // we can ignore error msg, because we don't display it anyway
    }
  }
}