package com.github.opengrabeso.mixtio
package frontend
package views.about

import routing._
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class AboutPageViewFactory(
  application: Application[RoutingState],
) extends ViewFactory[AboutPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[AboutPageState.type]) = {
    // Main model of the view
    val model = ModelProperty(
      AboutPageModel(false, Seq.empty)
    )

    val presenter = new AboutPagePresenter(model, application)
    val view = new AboutPageView(model, presenter)
    (view, presenter)
  }

  private object NonEmptyStringValidator extends Validator[String] {
    override def apply(element: String): Future[ValidationResult] = Future.successful {
      if (element.nonEmpty) Valid else Invalid("") // we can ignore error msg, because we don't display it anyway
    }
  }
}