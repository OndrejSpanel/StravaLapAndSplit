package com.github.opengrabeso.mixtio.frontend
package routing

import views._
import io.udash._

class StatesToViewFactoryDef extends ViewFactoryRegistry[RoutingState] {
  def matchStateToResolver(state: RoutingState): ViewFactory[_ <: RoutingState] =
    state match {
      case RootState => new RootViewFactory
      case AboutPageState => new about.AboutPageViewFactory(ApplicationContext.application)
      case DummyPageState => new Dummy.PageViewFactory(ApplicationContext.application)
    }
}