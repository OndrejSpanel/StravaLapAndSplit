package com.github.opengrabeso.mixtio.facade

import scala.scalajs.js
import scala.scalajs.js.annotation._

@JSGlobal
@js.native
object mapboxgl extends js.Any {
  var accessToken: String = js.native

  @js.native
  class Map(options: js.Object) extends js.Object {
    def addSource(name: String, content: js.Object): Unit = js.native
    def addLayer(layer: js.Object): Unit = js.native
    def on(event: String, callback: js.Function0[Unit]): Unit = js.native
  }
}
