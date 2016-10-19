package com.github.opengrabeso.stravalas

import spark.{Request, Response}


@Handle("/activity")
object ActivityPage extends DefineRequest {
  override def html(request: Request, resp: Response) = {

<html>
  {
    val session = request.session()
    val authToken = session.attribute[String]("authToken")
    val mapBoxToken = session.attribute[String]("mapboxToken")
    val actId = request.queryParams("activityId")
    val laps = Main.getEventsFrom(authToken, actId)
    session.attribute("events-" + actId, laps)

  <head>
    <meta charset="utf-8"/>
    <title>Strava Split And Lap</title>

    <meta name='viewport' content='initial-scale=1,maximum-scale=1,user-scalable=no' />
    <script src='https://api.tiles.mapbox.com/mapbox-gl-js/v0.23.0/mapbox-gl.js'></script>
    <link href='https://api.tiles.mapbox.com/mapbox-gl-js/v0.23.0/mapbox-gl.css' rel='stylesheet' />

    <style>
      .activityTable {{
      border: 0;
      border-collapse: collapse;
      }}
      td, th {{
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

    <script type="text/javascript">
      {
      //language=JavaScript
      xml.Unparsed(s"""
      var id = "$actId";
      var authToken = "$authToken";
      // events are: ["split", 0, 0.0, "Run"] - kind, time, distance, sport
      var events = [
        ${laps.editableEvents.mkString("[", "],[", "]")}
      ];

    /**
     * @param {String} id
     * @param event
     * @return {String}
     */
    function splitLink(id, event) {
      var time = event[1];
      var splitWithEvents =
              '  <input type="hidden" name="id" value="' + id + '"/>' +
              '  <input type="hidden" name="auth_token" value="' + authToken + '"/>' +
              '  <input type="hidden" name="operation" value="split"/>' +
              '  <input type="hidden" name="time" value="' + time + '"/>' +
              '  <input type="submit" value="Download"/>';

      var nextSplit = null;
      events.forEach( function(e) {
        splitWithEvents = splitWithEvents + '<input type="hidden" name="events" value="' + e[0] + '"/>';
        if (e[0].lastIndexOf("split", 0) == 0 && e[1] > time && nextSplit == null) {
          nextSplit = e;
        }
      });

      if (nextSplit == null) nextSplit = events[events.length-1];

      var description = "";
      if (nextSplit) {
        var km = (nextSplit[2] - event[2])/1000;
        var duration = nextSplit[1] - event[1];
        var paceSecKm = km > 0 ? duration / km : 0;
        var paceMinKm = paceSecKm / 60;
        var speedKmH = duration > 0 ? km * 3600 / duration : 0;
        description = km.toFixed(2) + " km / " + paceMinKm.toFixed(2) + " min/km / " + speedKmH.toFixed(1) + " km/h";
      }
      return '<form action="download" method="post">' + splitWithEvents + description + '</form>';

    }

    function initEvents() {
      events.forEach(function(e){
        if (e[0].lastIndexOf("split",0) == 0) {
          addEvent(e);
        }
      });
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
     * */
    function changeEvent(item, newValue) {
      var itemTime = item.id;
      events.forEach(function(e) {
        if (e[1] == itemTime) e[0] = newValue;
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
    }
    """)
      }
  </script>


</head>

<body>

<a href={laps.id.link}> {laps.id.name} </a>

<form action ="download" method="post">
  <input type="hidden" name="id" value={laps.id.id.toString}/>
  <input type="hidden" name="auth_token" value={authToken.toString}/>
  <input type="hidden" name="operation" value="copy"/>
  <input type="submit" value="Backup original activity"/>
</form>

  <table class="activityTable">
    <tr>
      <th>Event</th>
      <th>Time</th>
      <th>km</th>
      <th>Sport</th>
      <th>Action</th>
    </tr>{val ees = laps.editableEvents
  var lastSport = ""
  var lastTime = -1
  for ((t, i) <- laps.events.zipWithIndex) yield {
    val t = laps.events(i)
    val ee = ees(i)
    val action = ee.action
    val sport = if (ee.sport == lastSport) "" else ee.sport
    lastSport = ee.sport
    <tr>
      <td> {xml.Unparsed(t.description)} </td>
      <td> {Main.displaySeconds(t.stamp.time)} </td>
      <td> {Main.displayDistance(t.stamp.dist)} </td>
      <td> {sport} </td>
      <td>
        {val types = t.listTypes
        if (types.length != 1 && lastTime != t.stamp.time) {
          lastTime = t.stamp.time
          <select id={t.stamp.time.toString} name="events" onchange="changeEvent(this, this.options[this.selectedIndex].value)">
            {for (et <- types) yield {
            <option value={et.id} selected={if (action == et.id) "" else null}>
              {et.display}
            </option>
          }}
          </select>
        } else {
          {Events.typeToDisplay(types, types(0).id)}
          <input type="hidden" name="events" value={t.defaultEvent}/>
      }}
      </td>
      <td class="cellNoBorder" id={s"link${t.stamp.time.toString}"}> </td>
    </tr>
  }}
  </table>

  <div id='map'></div>

  <script type="text/javascript">initEvents()</script>

  <script>{
    //language=JavaScript
    xml.Unparsed(s"""
    function renderEvents(events, route) {
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
            "color": "#444",
            "opacity": 0.5
          }
        };

        markers.push(marker)
      });

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
              "icon": "triangle", // star, marker, triangle - see https://github.com/mapbox/mapbox-gl-styles/tree/master/sprites/basic-v9/_svg
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

    var lat = ${laps.id.lat};
    var lon = ${laps.id.lon};
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
        }
      };
    xmlHttp.open("GET", "route-data?id=" + id + "&auth_token=" + authToken, true); // true for asynchronous
      xmlHttp.send(null)});
    """)}
  </script>

</body>
    }
</html>

  }
}
