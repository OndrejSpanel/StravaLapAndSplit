package com.github.opengrabeso.mixtio
package frontend.views

import frontend.model._
import facade.UdashApp._
import facade._
import org.scalajs.dom

import scala.scalajs.js
import js.Dynamic.literal
import js.JSConverters._

object MapboxMap {

  def display(geojson: String, events: Seq[EditEvent]): Unit = {
    val route = js.JSON.parse(geojson).asInstanceOf[js.Array[js.Array[Double]]]
    val routeX = route.map(_(0))
    val routeY = route.map(_(1))
    val minX = routeX.min
    val maxX = routeX.max
    val minY = routeY.min
    val maxY = routeY.max

    val bounds = LngLatBounds(
      _ne = LngLat(
        lat = minY,
        lng = minX
      ),
      _sw = LngLat (
        lat = maxY,
        lng = maxX
      )
    )

    mapboxgl.accessToken = mapBoxToken
    val map = new mapboxgl.Map(js.Dynamic.literal(
      container = "map", // container id
      style = "mapbox://styles/ospanel/cjkbfwccz11972rmt4xvmvme6", // stylesheet location
      center = js.Array((minX + maxX) / 2, (minY + maxY) / 2), // starting position [lng, lat]
      zoom = 13 // starting zoom
    ))
    val fitOptions = literal(
      padding = 50
    )
    map.fitBounds(bounds, fitOptions)


    def moveHandler() = {
      val existing = map.getSource("events")
      if (existing.isDefined) {
        val data = existing.get._data
        renderGrid(map, data.features.asInstanceOf[js.Array[js.Dynamic]](0).geometry.coordinates.asInstanceOf[js.Array[Double]])
      }
    }
    map.on("moveend", () => moveHandler())
    map.on("move", () => moveHandler())


    map.on("load", { () =>
      renderRoute(map, route)
      renderEvents(map, events, route)
      renderGrid(map, route(0))
    })

  }

  def renderRoute(map: mapboxgl.Map, route: js.Array[js.Array[Double]]): Unit = {

    val routeLL = route.map(i => js.Array(i(0), i(1)))

    map.addSource("route", literal (
      `type` = "geojson",
      data = literal(
        `type` = "Feature",
        properties = literal(),
        geometry = literal(
          `type` = "LineString",
          coordinates = routeLL
        )
      )
    ))

    map.addLayer(literal(
      id ="route",
      `type` = "line",
      source = "route",
      layout = literal(
        "line-join" -> "round",
        "line-cap" -> "round"
      ),
      paint = literal(
        "line-color" -> "#F44",
        "line-width" -> 3
      )
    ))
  }

  def lerp(a: Double, b: Double, f: Double) = {
    a + (b - a) * f
  }

  def findPoint(route: js.Array[js.Array[Double]], time: Double): js.Array[Double] = {
    // interpolate between close points if necessary
    val (before, after) = route.span(_(3) < time)
    if (before.isEmpty) after.head
    else if (after.isEmpty) before.last
    else {
      val prev = before.last
      val next = after.head
      val f: Double = if (time < prev(2)) 0
      else if (time > next(2)) 1
      else (time - prev(2)) / (next(2) - prev(2))
      js.Array(
        lerp(prev(0), next(0), f),
        lerp(prev(1), next(1), f),
        lerp(prev(2), next(2), f), // should be time
        lerp(prev(3), next(3), f)
      )
    }
  }


  def mapEventData(events: Seq[EditEvent], route: js.Array[js.Array[Double]]): Seq[js.Object] = {
    val dropStartEnd = events.drop(1).dropRight(1)
    val markers = dropStartEnd.map { e =>
      // ["split", 0, 0.0, "Run"]
      val r = findPoint(route, e.time)
      val marker = literal(
        `type` = "Feature",
        geometry = literal(
          `type` = "Point",
          coordinates = js.Array(r(0), r(1))
        ),
        properties = literal(
          title = e.originalAction,
          icon = "circle",
          description = "" , // TODO: getSelectHtml(e(1), e(3))
          color = "#444",
          opacity = 0.5,
        )
      )
      marker
    }
    markers
  }


  def renderEvents(map: mapboxgl.Map, events: Seq[EditEvent], route: js.Array[js.Array[Double]]): Unit = {
    val eventMarkers = mapEventData(events, route)
    val routeLL = route.map(i => js.Array(i(0), i(1)))

    val startMarker = literal(
      `type` = "Feature",
      geometry = literal(
        `type` = "Point",
        coordinates = routeLL(0)
      ),
      properties = literal(
        title = "Begin",
        description = events(0).originalAction, // TODO: description
        icon = "triangle",
        color = "#F22",
        opacity = 1
      )
    )
    val endMarker = literal(
      `type` = "Feature",
      geometry = literal(
        `type` = "Point",
        coordinates = routeLL(routeLL.length - 1)
      ),
      properties = literal(
        title = "End",
        description = events.last.originalAction, // TODO: description
        icon = "circle",
        color = "#2F2",
        opacity = 0.5
      )
    )

    val markers = startMarker +: eventMarkers :+ endMarker

    val iconLayout = literal(
      "icon-image" -> "{icon}-11",
      "text-field" -> "{title}",
      "text-font" -> js.Array("Open Sans Semibold", "Arial Unicode MS Bold"),
      "text-size" -> 10,
      "text-offset" -> js.Array(0, 0.6),
      "text-anchor" -> "top"
    )

    map.addSource("events", literal(
      `type` = "geojson",
      data = literal(
        `type` = "FeatureCollection",
        features = markers.toJSArray
      )
    ))
    map.addLayer(literal(
      id = "events",
      `type` = "symbol",
      source = "events",
      layout = iconLayout
    ))
    var lastKm = 0.0
    val kmMarkers = route.flatMap {r =>
      val dist = r(3) / 1000
      val currKm = Math.floor(dist)
      if (currKm > lastKm) {
        val kmMarker = literal(
          `type` = "Feature",
          geometry = literal(
            `type` = "Point",
            coordinates = js.Array(r(0), r(1))
          ),
          properties = literal(
            title = currKm + " km",
            icon = "circle-stroked",
            color = "#2F2",
            opacity = 0.5
          )
        )
        lastKm = currKm
        Some(kmMarker)
      } else None
    }
    map.addSource("kms", literal(
      `type` = "geojson",
      data = literal(
        `type` = "FeatureCollection",
        features = kmMarkers.toJSArray
      )
    ))
    map.addLayer(literal(
      id = "kms",
      `type` = "symbol",
      source = "kms",
      layout = iconLayout
    ))
  }

  case class GridAndAlpha(grid: js.Array[js.Array[js.Array[Double]]], alpha: Double)

  def generateGrid(bounds: LngLatBounds, size: Size, fixedPoint: js.Array[Double]): GridAndAlpha = {
    // TODO: pad the bounds to make sure we draw the lines a little longer
    val grid_box = bounds
    val avg_y = (grid_box._ne.lat + grid_box._sw.lat) * 0.5
    // Meridian length is always the same
    val meridian = 20003930.0
    val equator = 40075160
    val parallel = Math.cos(avg_y * Math.PI / 180) * equator
    val grid_distance = 1000.0
    val grid_step_x = grid_distance / parallel * 360
    val grid_step_y = grid_distance / meridian * 180
    val minSize = Math.max(size.x, size.y)
    val minLineDistance = 10
    val maxLines = minSize / minLineDistance
    val latLines = _latLines(bounds, fixedPoint, grid_step_y, maxLines)
    val lngLines = _lngLines(bounds, fixedPoint, grid_step_x, maxLines)
    val alpha = Math.min(latLines.alpha, lngLines.alpha)
    if (latLines.lines.length > 0 && lngLines.lines.length > 0) {
      val grid = latLines.lines.flatMap(i => if (Math.abs(i) > 90) {
          None
        } else {
          Some(_horizontalLine(bounds, i, alpha))
        })
      grid ++= lngLines.lines.map { i =>
        _verticalLine(bounds, i, alpha)
      }
      return GridAndAlpha(grid, alpha)
    }
    GridAndAlpha(js.Array(), 0)
  }

  case class Size(x: Double, y: Double)

  def renderGrid(map: mapboxgl.Map, fixedPoint: js.Array[Double]): Unit = {
    val container = map.getContainer()
    val size = Size(
      x = container.clientWidth,
      y = container.clientHeight
    )
    val gridAndAlpha = generateGrid(map.getBounds(), size, fixedPoint)
    val grid = gridAndAlpha.grid
    val alpha = gridAndAlpha.alpha
    val gridData = new js.Object {
      var `type` = "Feature"
      var properties = new js.Object {}
      var geometry = new js.Object {
        var `type` = "MultiLineString"
        var coordinates = grid
      }
    }
    val existing = map.getSource("grid")
    if (existing.isDefined) {
      existing.get.setData(gridData)
      map.setPaintProperty("grid", "line-opacity", alpha)
      map.setLayoutProperty("grid", "visibility", if (alpha > 0) "visible" else "none")
    } else {
      map.addSource("grid", new js.Object {
        var `type` = "geojson"
        var data = gridData
      })
      map.addLayer(new js.Object {
        var id = "grid"
        var `type` = "line"
        var source = "grid"
        var layout = new js.Object {
          var `line-join` = "round"
          var `line-cap` = "round"
        }
        var paint = new js.Object {
          var `line-color` = "#e40"
          var `line-width` = 2
          var `line-opacity` = alpha
        }
      })
    }
    // icon list see https://www.mapbox.com/maki-icons/ or see https://github.com/mapbox/mapbox-gl-styles/tree/master/sprites/basic-v9/_svg
    // basic geometric shapes, each also with - stroke variant:
    //   star, star-stroke, circle, circle-stroked, triangle, triangle-stroked, square, square-stroked
    //
    // specific, but generic enough:
    //   marker, cross, heart (Maki only?)
  }

  private case class AlphaLines(alpha: Double, lines: js.Array[Double])

  private def _latLines(bounds: LngLatBounds, fixedPoint: js.Array[Double], yticks: Double, maxLines: Double): AlphaLines = {
    _lines(bounds._sw.lat, bounds._ne.lat, yticks, maxLines, fixedPoint(1))
  }

  private def _lngLines(bounds: LngLatBounds, fixedPoint: js.Array[Double], xticks: Double, maxLines: Double): AlphaLines = {
    _lines(bounds._sw.lng, bounds._ne.lng, xticks, maxLines, fixedPoint(0))
  }

  private def _lines(low: Double, high: Double, ticks: Double, maxLines: Double, fixedCoord: Double): AlphaLines = {
    val delta = high - low
    val lowAligned = Math.floor((low - fixedCoord) / ticks) * ticks + fixedCoord
    val lines = new js.Array[Double]
    if (delta / ticks <= maxLines) {
      var i = lowAligned;
      while (i <= high) {
        lines.push(i)
        i += ticks
      }
    }
    val aScale = 15
    val a = maxLines / aScale / (delta / ticks)
    AlphaLines(
      lines = lines,
      alpha = Math.min(1, Math.sqrt(a))
    )
  }

  private def _verticalLine(bounds: LngLatBounds, lng: Double, alpha: Double) = {
    js.Array(js.Array(lng, bounds.getNorth()), js.Array(lng, bounds.getSouth()))
  }

  private def _horizontalLine(bounds: LngLatBounds, lat: Double, alpha: Double) = {
    js.Array(js.Array(bounds.getWest(), lat), js.Array(bounds.getEast(), lat))
  }
}
