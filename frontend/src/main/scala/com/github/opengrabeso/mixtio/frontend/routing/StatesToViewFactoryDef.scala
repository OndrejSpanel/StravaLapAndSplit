package com.github.opengrabeso.mixtio.frontend
package routing

import views._
import io.udash._

class StatesToViewFactoryDef extends ViewFactoryRegistry[RoutingState] {
  def matchStateToResolver(state: RoutingState): ViewFactory[_ <: RoutingState] =
    state match {
      case RootState => new Root.PageViewFactory(ApplicationContext.application, ApplicationContext.userContextService)
      case SelectPageState => new select.PageViewFactory(ApplicationContext.application, ApplicationContext.userContextService)
      case SettingsPageState => new settings.PageViewFactory(ApplicationContext.application, ApplicationContext.userContextService)
      case PushPageState(sessionId) => new push.PageViewFactory(ApplicationContext.application, ApplicationContext.userContextService, sessionId)
      case EditPageState(activities) => new edit.PageViewFactory(ApplicationContext.application, ApplicationContext.userContextService, activities)
    }
}