package com.github.opengrabeso.mixtio
package frontend
package views.edit
import com.github.opengrabeso.mixtio.facade.UdashApp
import common.model._
import routing.{EditPageState, RoutingState}
import io.udash._

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
  activities: Seq[FileId]
) extends ViewFactory[EditPageState] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[EditPageState]) = {
    val model = ModelProperty(PageModel(true, activities))

    for {
      mergedId <- userService.api.get.mergeActivitiesToEdit(activities, UdashApp.sessionId)
    } {
      model.subProp(_.merged).set(mergedId)
      model.subProp(_.loading).set(false)
    }

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}