package com.github.opengrabeso.stravalas
package requests

import com.github.opengrabeso.stravalas.Main.ActivityEvents
import org.joda.time.{DateTime => ZonedDateTime, Seconds}
import spark.{Request, Response, Session}

import scala.xml.NodeSeq

protected case class ActivityContent(head: NodeSeq, body: NodeSeq)

object ActivityRequest {
  def htmlSelectEvent(time: String, types: Array[EventKind], action: String) = {
    <select id={time} name="events" onchange={s"changeEvent(this, this.options[this.selectedIndex].value, $time)"}>
      {for (et <- types) yield {
      <option value={et.id} selected={if (action == et.id) "" else null}>
        {et.display}
      </option>
    }}
    </select>
  }

}

trait ActivityRequestHandler {
  import ActivityRequest._

  protected def activityHtmlContent(actId: String, activityData: ActivityEvents, session: Session, resp: Response): ActivityContent = {
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val headContent = <meta name='viewport' content='initial-scale=1,maximum-scale=1,user-scalable=no' />
      <script src='https://api.tiles.mapbox.com/mapbox-gl-js/v0.23.0/mapbox-gl.js'></script>
        <link href='https://api.tiles.mapbox.com/mapbox-gl-js/v0.23.0/mapbox-gl.css' rel='stylesheet' />

      <style>
        .activityTable {{
        border: 0;
        border-collapse: collapse;
        }}
        .activityTable td, .activityTable th {{
        border: 1px solid black;
        }}
        .cellNoBorder {{
        border: 0;
        }}

        #map {{
        height: 500px;
        width: 800px;
        }}
      </style>

      <script type="text/javascript">{activityJS(actId, activityData, auth.token)}</script>

    val bodyContent = <table class="activityTable">
      <tr>
        <th>Event</th>
        <th>Time</th>
        <th>km</th>
        <th>Action</th>
      </tr>{val ees = activityData.editableEvents
      var lastSport = ""
      var lastTime = Option.empty[ZonedDateTime]
      val startTime = activityData.id.startTime
      for ((t, i) <- activityData.events.zipWithIndex) yield {
        val t = activityData.events(i)
        val ee = ees(i)
        val action = ee.action
        val eTime = activityData.secondsInActivity(t.stamp)
        <tr>
          <td> {xml.Unparsed(t.description)} </td>
          <td> {Main.displaySeconds(eTime)} </td>
          <td> {Main.displayDistance(activityData.distanceForTime(t.stamp))} </td>
          <td>
            {val types = t.listTypes
          if (types.length != 1 && !lastTime.contains(t.stamp)) {
            lastTime = Some(t.stamp)
            htmlSelectEvent(eTime.toString, t.listTypes, action)
          } else {
            {Events.typeToDisplay(types, types(0).id)}
            <input type="hidden" name="events" value={t.defaultEvent}/>
          }}
          </td>
          <td class="cellNoBorder" id={s"link${eTime.toString}"}> </td>
        </tr>
      }}
    </table> ++ {
      if (activityData.hasGPS) {
        <div id='map'></div>
          <script>
            {mapJS(activityData, auth.mapboxToken)}
          </script>
      } else <div></div>
    } :+ <script type="text/javascript">initEvents()</script>

    ActivityContent(headContent, bodyContent)
  }

  private def activityJS(actId: String, activityData: ActivityEvents, authToken: String) = {
    //language=JavaScript
    xml.Unparsed(
      s"""
      var id = "$actId";
      var authToken = "$authToken";
      // events are: ["split", 0, 0.0, "Run"] - kind, time, distance, sport
      var events = [
        ${activityData.editableEvents.mkString("[", "],[", "]")}
      ];

      // callback, should update the map when events are changed
      var onEventsChanged = function() {};

    /**
     * @param {String} id
     * @param {String} time
     * @param {String} action
     * @param {String} value
     * @return {String}
     */
    function linkWithEvents(id, time, action, value) {
      var splitWithEvents =
              '  <input type="hidden" name="id" value="' + id + '"/>' +
              '  <input type="hidden" name="auth_token" value="' + authToken + '"/>' +
              '  <input type="hidden" name="operation" value="split"/>' +
              '  <input type="hidden" name="time" value="' + time + '"/>' +
              '  <input type="submit" value="' + value + '"/>';

      events.forEach( function(e) {
        splitWithEvents = splitWithEvents + '<input type="hidden" name="events" value="' + e[0] + '"/>';
      });

      return '<form action="' + action + '" method="get" style="display:inline-block">' + splitWithEvents  + '</form>';
    }
    /**
     * @param {String} id
     * @param event
     * @return {String}
     */
    function splitLink(id, event) {
      var time = event[1];
      var downloadButton = linkWithEvents(id, time, "download", "Download");
      var uploadButton = linkWithEvents(id, time, "upload-strava", "Upload to Strava");

      var nextSplit = null;
      events.forEach( function(e) {
        if (e[0].lastIndexOf("split", 0) == 0 && e[1] > time && nextSplit == null) {
          nextSplit = e;
        }
      });
      if (nextSplit == null) nextSplit = events[events.length-1];

      var description = "???";
      if (nextSplit) {
        var km = (nextSplit[2] - event[2])/1000;
        var duration = nextSplit[1] - event[1];
        var paceSecKm = km > 0 ? duration / km : 0;
        var paceMinKm = paceSecKm / 60;
        var speedKmH = duration > 0 ? km * 3600 / duration : 0;
        description = km.toFixed(2) + " km / " + paceMinKm.toFixed(2) + " min/km / " + speedKmH.toFixed(1) + " km/h";
      }
      return downloadButton  + uploadButton + description;

    }

    function initEvents() {
      events.forEach(function(e){
        if (e[0].lastIndexOf("split",0) == 0) {
          addEvent(e);
        }
      });
    }

    function selectOption(e) {
      var tableOption = document.getElementById(e[1]);
      // select appropriate option
      tableOption.value = e[0];

      // we need to update the table source, because it is used to create map popups
      // http://stackoverflow.com/a/40766724/16673
      var opts = tableOption.getElementsByTagName('option');
      for (var i = 0; i < opts.length; i++)
          opts[i].removeAttribute('selected');
      var checked = tableOption.querySelector('option:checked');
      checked.setAttribute('selected', 'selected');
    }

    function addEvent(e) {
      var tableLink = document.getElementById("link" + e[1]);
      tableLink.innerHTML = splitLink(id, e);
    }

    /** @param {String} time */
    function removeEvent(time) {
      var tableLink = document.getElementById("link" + time);
      tableLink.innerHTML = "";
    }

    /**
    * @param {Element} item
    * @param {String} newValue
    * @param {String} itemTime
    * */
    function changeEvent(item, newValue, itemTime) {
      events.forEach(function(e) {
        if (e[1] == itemTime) {
          e[0] = newValue;
          selectOption(e);
        }
      });

      events.forEach(function(e) {
        if (e[1] == itemTime && e[0].lastIndexOf("split", 0) === 0){
          addEvent(e);
        } else {
          removeEvent(itemTime);
        }
      });

      // without changing the active event first it is often not updated at all, no idea why
      events.forEach(function (e) {
        if (e[0].lastIndexOf("split", 0) === 0) {
          addEvent(e);
        }
      });


      // execute the callback
      onEventsChanged();
    }
    """)
  }

  private def mapJS(activityData: ActivityEvents, mapBoxToken: String) = {
    //language=JavaScript
    xml.Unparsed(
      s"""
    // MapBox map handling

    /**
     * @param {String} eTime time of the event
     * */
    function getSelectHtml(eTime) {
      var tableOption = document.getElementById(eTime);
      var html = tableOption.innerHTML;
      var value = tableOption.value;
      return '<select onchange="changeEvent(this, this.options[this.selectedIndex].value,' + eTime + ')">' + html + '</select>';
    }
    function mapEventData(events, route) {
      var markers = [];

      function findPoint(route, time) {
        return route.filter(function(r) {
          return r[2] == time
        })[0]
      }

      var dropStartEnd = events.slice(1, -1);

      dropStartEnd.forEach(function(e) {
        // ["split", 0, 0.0, "Run"]
        var r = findPoint(route, e[1]);

        var marker = {
          "type": "Feature",
          "geometry": {
            "type": "Point",
            "coordinates": [r[0], r[1]]
          },
          "properties": {
            "title": e[0],
            "icon": "circle",
            "description": getSelectHtml(e[1]),
            "color": "#444",
            "opacity": 0.5
          }
        };

        markers.push(marker)
      });
      return markers;
    }

    function renderEvents(events, route) {
      var markers = mapEventData(events, route);

      map.addSource("events", {
        "type": "geojson",
        "data": {
          "type": "FeatureCollection",
          "features": markers
        }
      });

      map.addLayer({
        "id": "events",
        "type": "symbol",
        "source": "events",
        "layout": {
          "icon-image": "{icon}-11",
          "text-field": "{title}",
          "text-font": ["Open Sans Semibold", "Arial Unicode MS Bold"],
          "text-size": 10,
          "text-offset": [0, 0.6],
          "text-anchor": "top"
        }
      });

      var lastKm = 0;
      var kmMarkers = [];
      route.forEach(function(r){
        var dist = r[3] / 1000;
        var currKm = Math.floor(dist);
        if (currKm > lastKm) {

          var kmMarker = {
            "type": "Feature",
            "geometry": {
              "type": "Point",
              "coordinates": [r[0], r[1]]
            },
            "properties": {
              "title": currKm + " km",
              "icon": "circle-stroked",
              "color": "#2F2",
              "opacity": 0.5
            }
          };

          kmMarkers.push(kmMarker);
          lastKm = currKm
        }
      });

      map.addSource("kms", {
        "type": "geojson",
        "data": {
          "type": "FeatureCollection",
          "features": kmMarkers
        }
      });

      map.addLayer({
        "id": "kms",
        "type": "symbol",
        "source": "kms",
        "layout": {
          "icon-image": "{icon}-11",
          "text-field": "{title}",
          "text-font": ["Open Sans Semibold", "Arial Unicode MS Bold"],
          "text-size": 10,
          "text-offset": [0, 0.6],
          "text-anchor": "top"
        }
      });
    }

    function renderRoute(route) {
      var routeLL = route.map(function(i){
        return [i[0], i[1]]
      });

      map.addSource("route", {
        "type": "geojson",
        "data": {
          "type": "Feature",
          "properties": {},
          "geometry": {
            "type": "LineString",
            "coordinates": routeLL
          }
        }
      });
      map.addLayer({
        "id": "route",
        "type": "line",
        "source": "route",
        "layout": {
          "line-join": "round",
          "line-cap": "round"
        },
        "paint": {
          "line-color": "#F44",
          "line-width": 3
        }
      });

      // icon list see https://www.mapbox.com/maki-icons/ or see https://github.com/mapbox/mapbox-gl-styles/tree/master/sprites/basic-v9/_svg
      // basic geometric shapes, each also with - stroke variant:
      //   star, star-stroke, circle, circle-stroked, triangle, triangle-stroked, square, square-stroked
      //
      // specific, but generic enough:
      //   marker, cross, heart (Maki only?)
      map.addSource("points", {
        "type": "geojson",
        "data": {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {
              "type": "Point",
              "coordinates": routeLL[0]
            },
            "properties": {
              "title": "Begin",
              "icon": "triangle",
              "color": "#F22",
              "opacity": 1
            }
          }, {
            "type": "Feature",
            "geometry": {
              "type": "Point",
              "coordinates": routeLL[routeLL.length-1]
            },
            "properties": {
              "title": "End",
              "icon": "circle",
              "color": "#2F2",
              "opacity": 0.5
            }
          }]
        }
      });

      map.addLayer({
        "id": "points",
        "type": "symbol",
        "source": "points",
        "layout": {
          "icon-image": "{icon}-15",
          //"icon-opacity": "1",
          //"icon-color": "{color}", // not working at the moment - see https://github.com/mapbox/mapbox-gl-js/issues/2730
          "text-field": "{title}",
          "text-font": ["Open Sans Semibold", "Arial Unicode MS Bold"],
          "text-offset": [0, 0.6],
          "text-anchor": "top"
        }
      });


    }

    if (${activityData.hasGPS}) {
      var lat = ${activityData.lat};
      var lon = ${activityData.lon};
      mapboxgl.accessToken = "$mapBoxToken";
      var map = new mapboxgl.Map({
        container: 'map',
        style: 'mapbox://styles/mapbox/outdoors-v9',
        center: [lon, lat],
        zoom: 12
      });

      map.on('load', function () {

        var xmlHttp = new XMLHttpRequest();
        xmlHttp.onreadystatechange = function() {
          if (xmlHttp.readyState == 4 && xmlHttp.status == 200) {
            var route = JSON.parse(xmlHttp.responseText);
            renderRoute(route);
            renderEvents(events, route);

            onEventsChanged = function() {
              var eventsData = mapEventData(events, route);

              var geojson = {
                "type": "FeatureCollection",
                "features": eventsData
              };

              map.getSource("events").setData(geojson);
            };

            map.on('mousemove', function (e) {
                var features = map.queryRenderedFeatures(e.point, { layers: ['events'] });
                map.getCanvas().style.cursor = (features.length) ? 'pointer' : '';
            });

            map.on('click', function (e) {
                var features = map.queryRenderedFeatures(e.point, { layers: ['events'] });

                if (!features.length) {
                    return;
                }

                var feature = features[0];

                // Populate the popup and set its coordinates
                // based on the feature found.
                var popup = new mapboxgl.Popup()
                    .setLngLat(feature.geometry.coordinates)
                    .setHTML(feature.properties.description)
                    .addTo(map);
            });



          }

        };
        xmlHttp.open("GET", "route-data?id=" + encodeURIComponent(id) + "&auth_token=" + authToken, true); // true for asynchronous
        xmlHttp.send(null)});

    }

    """)
  }
}

object ActivityPage extends DefineRequest("/activity") with ActivityRequestHandler {

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val actId = request.queryParams("activityId")
    val activityData = Main.getEventsFrom(auth.token, actId)

    Storage.store("events-" + actId, auth.userId, activityData, "digest" -> activityData.id.digest)

    val content = activityHtmlContent(actId, activityData, session, resp)

    <html>
      <head>
        <title>Stravamat</title>
        {headPrefix}
        {content.head}
      </head>
      <body>
        {bodyHeader(auth)}
        <a href={activityData.id.link}> {activityData.id.name} </a>

        <form action ="download" method="get">
          <input type="hidden" name="id" value={activityData.id.id.toString}/>
          <input type="hidden" name="auth_token" value={auth.token.toString}/>
          <input type="hidden" name="operation" value="copy"/>
          <input type="submit" value="Backup original activity"/>
        </form>
        {content.body}
        {bodyFooter}
      </body>
    </html>
  }


}
