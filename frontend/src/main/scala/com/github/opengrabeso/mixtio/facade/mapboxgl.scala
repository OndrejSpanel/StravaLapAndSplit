package com.github.opengrabeso.mixtio.facade

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
trait LngLat extends js.Any {
  def lat: Double = js.native
  def lng: Double = js.native
}

object LngLat {
  // we construct LngLatLike, as LngLat constructor seems not to be available
  // https://docs.mapbox.com/mapbox-gl-js/api/#lnglatlike
  def apply(lng: Double, lat: Double): LngLat = js.Array(lng, lat).asInstanceOf[LngLat]
}

@js.native
trait LngLatBounds extends js.Any {
  def _ne: LngLat = js.native
  def _sw: LngLat = js.native

  def getNorth(): Double = js.native
  def getSouth(): Double = js.native
  def getEast(): Double = js.native
  def getWest(): Double = js.native
}

object LngLatBounds {
  // we construct LngLatBoundsLike, as LngLat constructor seems not to be available
  // see https://docs.mapbox.com/mapbox-gl-js/api/#lnglatboundslike
  def apply(_ne: LngLat, _sw: LngLat): LngLatBounds = js.Array(_ne, _sw).asInstanceOf[LngLatBounds]
}

@JSGlobal
@js.native
object mapboxgl extends js.Any {
  var accessToken: String = js.native

  // https://github.com/mapbox/mapbox-gl-js/blob/master/flow-typed/point-geometry.js
  @js.native
  class Point(val x: Double, val y: Double) extends js.Object {

  }

  // https://github.com/mapbox/mapbox-gl-js/blob/master/src/util/evented.js
  @js.native
  class Event(val `type`: String, data: js.Object = js.native) extends js.Object {

  }

  // https://github.com/mapbox/mapbox-gl-js/blob/master/src/ui/events.js
  @js.native
  class MapMouseEvent(`type`: String, data: js.Object = js.native) extends Event(`type`, data) {
    val target: Map = js.native
    val originalEvent: dom.MouseEvent = js.native
    val point: Point = js.native
    val lngLat: LngLat = js.native
  }

  // https://github.com/mapbox/mapbox-gl-js/blob/master/src/util/evented.js
  @js.native
  class Evented extends js.Object {
    def on(event: String, callback: js.Function1[Event, Unit]): Unit = js.native
    def off(event: String, callback: js.Function1[Event, Unit]): Unit = js.native
  }

  // https://github.com/mapbox/mapbox-gl-js/blob/master/src/ui/map.js
  @js.native
  class Map(options: js.Object) extends Evented {

    def queryRenderedFeatures(geometry: Point, options: js.Object): js.Array[js.Object] = js.native
    def queryRenderedFeatures(geometry: js.Array[Point], options: js.Object): js.Array[js.Object] = js.native
    def queryRenderedFeatures(options: js.Object = js.native): js.Array[js.Object] = js.native

    def addSource(name: String, content: js.Object): Unit = js.native
    def addLayer(layer: js.Object): Unit = js.native
    def getContainer(): dom.Element = js.native
    def getSource(name: String): js.UndefOr[js.Dynamic] = js.native
    def getBounds(): LngLatBounds = js.native
    def getCanvas(): dom.raw.HTMLCanvasElement = js.native
    def setPaintProperty(name1: String, name2: String, value: js.Any): Unit = js.native
    def setLayoutProperty(name1: String, name2: String, value: js.Any): Unit = js.native
    def fitBounds(bounds: LngLatBounds, options: js.Object = js.native, eventData: js.Object = js.native): Unit = js.native
  }
}
