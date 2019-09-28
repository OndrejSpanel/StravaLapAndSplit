package com.github.opengrabeso.mixtio
package frontend
package views.settings

import java.time.ZonedDateTime

import routing.{SettingsPageState, RoutingState}
import com.github.opengrabeso.mixtio.rest
import io.udash._

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
) extends ViewFactory[SettingsPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[SettingsPageState.type]) = {
    // TODO: do not switch to view until the API has returned
    val model = ModelProperty(
      PageModel(true, SettingsStorage())
    )

    for {
      userAPI <- facade.UdashApp.currentUserId.toOption.map(rest.RestAPIClient.api.userAPI)
      userSettings <- userAPI.allSettings
    } {
      model.subProp(_.settings).set(userSettings)
      model.subProp(_.loading).set(false)
    }

    val presenter = new PagePresenter(model, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}