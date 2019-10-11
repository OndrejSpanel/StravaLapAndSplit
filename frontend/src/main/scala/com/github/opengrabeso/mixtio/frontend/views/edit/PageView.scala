package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import common.Formatting
import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.table.UdashTable
import io.udash.component.ComponentId
import io.udash.css._
import io.udash.bootstrap._
import BootstrapStyles._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView {
  val s = EditPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  buttonOnClick(submitButton){presenter.gotoSelect()}

  model.subProp(_.routeJS).listen {
    _.foreach { geojson =>
      // events should always be ready before the route
      val events = model.subProp(_.events).get
      displayMapboxMap(geojson, events)
    }
  }

  def getTemplate: Modifier = {

    // value is a callback
    type EditAttrib = TableFactory.TableAttrib[EditEvent]
    val EditAttrib = TableFactory.TableAttrib

    //case class EditEvent(action: String, time: Int, km: Double, originalAction: String)
    val attribs = Seq[EditAttrib](
      EditAttrib("Action", (e, _, _) => e.action.render),
      EditAttrib("Time", (e, _, _) => Formatting.displaySeconds(e.time).render),
      EditAttrib("Distance", (e, _, _) => Formatting.displayDistance(e.time).render),
      EditAttrib("Event", (e, _, _) => e.originalAction.render),
    )

    val table = UdashTable(model.subSeq(_.events), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(TableFactory.headerFactory(attribs )),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    div(
      s.container,

      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            table.render,
          ).render
        )
      ),

      div(
        s.map,
        id := "map"
      )

    )
  }

  import facade.UdashApp._
  import facade.mapboxgl
  import scala.scalajs.js
  import js.Dynamic.literal
  import js.JSConverters._

  def displayMapboxMap(geojson: String, events: Seq[EditEvent]): Unit = {
    mapboxgl.accessToken = mapBoxToken;
    val map = new mapboxgl.Map(js.Dynamic.literal(
      container = "map", // container id
      style = "mapbox://styles/ospanel/cjkbfwccz11972rmt4xvmvme6", // stylesheet location
      center = js.Array(14.5, 49.8), // starting position [lng, lat]
      zoom = 13 // starting zoom
    ))
    val route = js.JSON.parse(geojson).asInstanceOf[js.Array[js.Array[Double]]]
    map.on("load", { () =>
      renderRoute(map, route)
      renderEvents(map, events, route)
    })

  }

  def renderRoute(map: mapboxgl.Map, route: js.Array[js.Array[Double]]): Unit = {

    val routeLL = route.map { i =>
      js.Array(i(0).toDouble, i(1).toDouble)
    }

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
    ));

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
      layout = literal(
        "icon-image" -> "{icon}-11",
        "text-field" -> "{title}",
        "text-font" -> js.Array("Open Sans Semibold", "Arial Unicode MS Bold"),
        "text-size" -> 10,
        "text-offset" -> js.Array(0, 0.6),
        "text-anchor" -> "top"
      )
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
        features = kmMarkers
      )
    ))
    map.addLayer(literal(
      id = "kms",
      `type` = "symbol",
      source = "kms",
      layout = literal(
        "icon-image" -> "{icon}-11",
        "text-field" -> "{title}",
        "text-font" -> js.Array("Open Sans Semibold", "Arial Unicode MS Bold"),
        "text-size" -> 10,
        "text-offset" -> js.Array(0, 0.6),
        "text-anchor" -> "top"
      )
    ))
  }

}
