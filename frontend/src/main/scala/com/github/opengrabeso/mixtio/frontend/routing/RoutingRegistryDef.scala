package com.github.opengrabeso.mixtio
package frontend.routing

import io.udash._
import common.model._

import scala.scalajs.js.URIUtils

class RoutingRegistryDef extends RoutingRegistry[RoutingState] {
  def matchUrl(url: Url): RoutingState =
    url2State("/" + url.value.stripPrefix("/").stripSuffix("/"))

  def matchState(state: RoutingState): Url = Url(state2Url(state))

  object URIEncoded {
    def apply(s: String): String = URIUtils.encodeURIComponent(s)
    def unapply(s: String): Option[String] = Some(URIUtils.decodeURIComponent(s))
  }
  private val (url2State, state2Url) = bidirectional {
    case "/" => SelectPageState
    case "/settings" => SettingsPageState
    case "/edit" / URIEncoded(s) =>
      EditPageState(Seq(FileId(s))) // TODO: parse (deserialize) multiple activities
    case "/edit" / URIEncoded(s1) / URIEncoded(s2) =>
      EditPageState(Seq(FileId(s1), FileId(s2))) // TODO: parse (deserialize) multiple activities
    case "/edit" => EditPageState(Nil)
  }
}