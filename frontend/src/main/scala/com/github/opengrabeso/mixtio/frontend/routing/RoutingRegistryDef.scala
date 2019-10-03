package com.github.opengrabeso.mixtio.frontend.routing

import io.udash._

class RoutingRegistryDef extends RoutingRegistry[RoutingState] {
  def matchUrl(url: Url): RoutingState =
    url2State("/" + url.value.stripPrefix("/").stripSuffix("/"))

  def matchState(state: RoutingState): Url = Url(state2Url(state))

  private val (url2State, state2Url) = bidirectional {
    case "/" => SelectPageState
    case "/settings" => SettingsPageState
    case "/dummy" => DummyPageState
  }
}