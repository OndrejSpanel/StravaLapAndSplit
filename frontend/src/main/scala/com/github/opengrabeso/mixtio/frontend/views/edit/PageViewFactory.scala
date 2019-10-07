package com.github.opengrabeso.mixtio
package frontend
package views.edit
import common.model._

import java.time.{ZoneOffset, ZonedDateTime}

import routing.{RoutingState, EditPageState}
import com.github.opengrabeso.mixtio.rest
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
  activities: Seq[FileId]
) extends ViewFactory[EditPageState] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[EditPageState]) = {
    val model = ModelProperty(PageModel(true, activities))

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}