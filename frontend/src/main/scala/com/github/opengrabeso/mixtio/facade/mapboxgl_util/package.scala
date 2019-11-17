package com.github.opengrabeso.mixtio.facade

import scala.scalajs.js

package object mapboxgl_util {
  abstract class LngLat extends js.Object {
    def lat: Double
    def lng: Double
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
}
