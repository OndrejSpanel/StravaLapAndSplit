package com.github.opengrabeso.mixtio
package frontend
package views.about

import com.github.opengrabeso.mixtio.rest
import routing._
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class AboutPageViewFactory(
  application: Application[RoutingState],
) extends ViewFactory[AboutPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[AboutPageState.type]) = {
    val model = ModelProperty(
      AboutPageModel(facade.UdashApp.currentUserId.toOption)
    )

    val presenter = new AboutPagePresenter(model, application)
    val view = new AboutPageView(model, presenter)
    (view, presenter)
  }
}