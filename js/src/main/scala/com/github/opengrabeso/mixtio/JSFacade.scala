package com.github.opengrabeso.mixtio

import scala.scalajs.js
import scala.scalajs.js.annotation._

@JSGlobalScope
@js.native
object JSFacade extends js.Any {
  def actIdName(): String = js.native
  def activityEvents(): AnyRef = js.native

  var id: String = js.native

  var events: js.Array[js.Array[String]] = js.native

  var onEventsChanged: js.Function0[Unit] = js.native
}
