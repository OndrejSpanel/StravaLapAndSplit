package com.github.opengrabeso.mixtio.facade

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
trait LatLong extends js.Any {
  def lat: Double = js.native
  def lng: Double = js.native
}

@js.native
trait Bounds extends js.Any {
  def _ne: LatLong = js.native
  def _sw: LatLong = js.native

  def getNorth(): Double = js.native
  def getSouth(): Double = js.native
  def getEast(): Double = js.native
  def getWest(): Double = js.native
}

@JSGlobal
@js.native
object mapboxgl extends js.Any {
  var accessToken: String = js.native

  @js.native
  class Map(options: js.Object) extends js.Object {
    def addSource(name: String, content: js.Object): Unit = js.native
    def addLayer(layer: js.Object): Unit = js.native
    def on(event: String, callback: js.Function0[Unit]): Unit = js.native
    def getContainer(): dom.Element = js.native
    def getSource(name: String): js.UndefOr[js.Dynamic] = js.native
    def getBounds(): Bounds = js.native
    def setPaintProperty(name1: String, name2: String, value: js.Any): Unit = js.native
    def setLayoutProperty(name1: String, name2: String, value: js.Any): Unit = js.native
  }
}
