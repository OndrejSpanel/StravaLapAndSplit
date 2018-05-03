package com.github.opengrabeso.stravimat
package requests

import com.github.opengrabeso.stravimat.Main._
import org.joda.time.{Seconds, DateTime => ZonedDateTime}
import spark.{Request, Response, Session}
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.IOUtils

import scala.xml.NodeSeq

protected case class ActivityContent(head: NodeSeq, body: NodeSeq)

object ActivityRequest {
  def htmlSelectEvent(time: String, types: Array[EventKind], action: String) = {
    <select id={time} name="events" onchange={s"changeEvent(this.options[this.selectedIndex].value, $time)"}>
      {for (et <- types) yield {
      <option value={et.id} selected={if (action == et.id) "" else null}>
        {et.display}
      </option>
    }}
    </select>
  }

}

trait ActivityRequestHandler extends UploadResults {
  import ActivityRequest._

  protected def activityHtmlContent(actId: FileId, activityData: ActivityEvents, session: Session, resp: Response): ActivityContent = {
    val auth = session.attribute[StravaAuthResult]("auth")

    val headContent = <meta name='viewport' content='initial-scale=1,maximum-scale=1,user-scalable=no' />
      <script src="static/jquery-3.2.1.min.js"></script>
      <script src="static/jquery.mpAjax.js"></script>
      <script src="static/download.js"></script>
      <script src="static/ajaxUtils.js"></script>
      <script src='https://api.tiles.mapbox.com/mapbox-gl-js/v0.23.0/mapbox-gl.js'></script>
      <link href='https://api.tiles.mapbox.com/mapbox-gl-js/v0.23.0/mapbox-gl.css' rel='stylesheet' />

      <link rel="stylesheet" type="text/css" href="static/activityPage.css"/>
      <link rel="stylesheet" type="text/css" href="static/page.css"/>

      <script type="text/javascript">{activityJS(actId, activityData)}</script>

      val stats = activityData.stats

    val bodyContent =
      <div class="top">
        <div class="act">
          <hr/>
          <table>
            <tr>
              <td>Duration</td><td>{displaySeconds(stats.totalTimeInSeconds.toInt)}</td>
            </tr>
            <tr>
              <td>Distance</td><td>{displayDistance(stats.distanceInM)}</td>
            </tr>
            <tr>
              <td>Elevation</td><td>{stats.elevation}</td>
            </tr>
          </table>
          <hr/>
          <form id="activity_form" action="upload-strava" method="post">
            <div class="aroundScrollingTable" id ="aroundScrollingTable">
          <table class="activityTable">
            <thead><tr>
              <th>Event</th>
              <th>Time</th>
              <th>km</th>
              <th>Action</th>
            </tr></thead><tbody>{val ees = activityData.editableEvents
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
                <td> <button type="button" onclick={s"selectMapEvent($i)"}>{displaySeconds(eTime)}</button></td>
                <td> {displayDistance(activityData.distanceForTime(t.stamp))} </td>
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
              <input type="hidden" name="id" value={actId.filename}/>
            }}
          </tbody></table>
          </div>
          </form>
          <div>
            <h3>Lap markers</h3>
            <button id="isCheckedLap" onClick="lapsClearAll()">Unselect all</button>
            <br />
            <button id="wasUserLap" onClick="lapsSelectUser()">Select user laps</button>
            <button id="wasSegment" onClick="lapsSelectByPredicate(wasSegment)">Select segments</button>
            <button id="wasHill" onClick="lapsSelectByPredicate(wasHill)">Select climbs/descends</button>
            <br />
            <button id="wasLongPause" onClick="lapsSelectLongPauses()">Select long pauses</button>
            <button id="wasAnyPause" onClick="lapsSelectAllPauses()">Select all pauses</button>
          </div>
          <div id="div_process">
            <h3>Process</h3>
            <button id="process_button" onclick="submitProcess()">Send selected to Strava</button>
            <button id="download_button" onclick="submitDownload()">Download as files</button>
            <button id="merge_button" onclick="submitEdit()">Merge and edit...</button>
            {uploadResultsHtml()}
          </div>
          <div id="div_no_process">
            <h3>
            Select at least one part of the activity to process it
            </h3>
          </div>
        </div>
        {if (activityData.hasGPS) {
        <div class="map clearfix" id='map'>
          <script>{mapJS(activityData, auth.mapboxToken)}</script>
        </div>
      } else {
        <div></div>
      }}
      </div>
      <script type="text/javascript">initEvents()</script>
    ActivityContent(headContent, bodyContent)
  }

  private def activityJS(fileId: FileId, activityData: ActivityEvents) = {
    val actIdName = fileId.filename
    //language=JavaScript
    xml.Unparsed(
      s"""
      var id = "$actIdName";
      // events are: ["split", 0, 0.0, "Run", "lap", "Start"] - kind, time, distance, sport, original kind, description
      var events = [
        ${activityData.editableEvents.mkString("[", "],\n        [", "]")}
      ];

      // callback, should update the map when events are changed
      var onEventsChanged = function() {};
      var currentPopup = undefined;

    /**
     * @param {String} id
     * @param event
     * @return {String}
     */
    function splitLink(id, event) {
      var time = event[1];
      var selectCheckbox = '<input type="checkbox" name="process_time=' + time + '"} checked=true onchange="onPartChecked(this)"></input>';

      var splitPrefix = "split";
      var nextSplit = null;
      events.forEach( function(e) {
        if (e[0].lastIndexOf(splitPrefix, 0) === 0 && e[1] > time && nextSplit == null) {
          nextSplit = e;
        }
      });
      if (nextSplit == null) nextSplit = events[events.length-1];

      var description = "???";
      if (nextSplit) {
        var km = (nextSplit[2] - event[2]) / 1000;
        var duration = nextSplit[1] - event[1];
        var kmH = true;
        var minKm = true;
        var sport = event[0].substring(splitPrefix.length);
        if (sport === "Run") kmH = false;
        if (sport === "Ride") minKm = false;

        var elements = [km.toFixed(1) + " km"];
        if (minKm) {
          var paceSecKm = km > 0 ? duration / km : 0;
          var paceMinKm = paceSecKm / 60;
          elements.push(paceMinKm.toFixed(2) + " min/km");
        }
        if (kmH) {
          var speedKmH = duration > 0 ? km * 3600 / duration : 0;
          elements.push(speedKmH.toFixed(1) + " km/h");
        }

        description = elements.join(" / ")
      }
      return selectCheckbox + description;

    }

    function initEvents() {
      //console.log("initEvents " + events.toString());
      events.forEach(function(e){
        if (e[0].lastIndexOf("split",0) === 0) {
          addEvent(e);
        } else {
          removeEvent(e[1])
        }
        selectOption(e);
      });
      showEventButtons();
      onPartChecked();
    }

    function selectOption(e) {
      //console.log("selectOption " + e[1]);
      var tableOption = document.getElementById(e[1]);
      if (tableOption) {
        // select appropriate option
        tableOption.value = e[0];

        // we need to update the table source, because it is used to create map popups
        // http://stackoverflow.com/a/40766724/16673
        var opts = tableOption.getElementsByTagName('option');
        for (var i = 0; i < opts.length; i++)
            opts[i].removeAttribute('selected');
        var checked = tableOption.querySelector('option:checked');
        if (checked) checked.setAttribute('selected', 'selected');
      }
    }

    function addEvent(e) {
      //console.log("Add event " + e[1]);
      var tableLink = document.getElementById("link" + e[1]);
      tableLink.innerHTML = splitLink(id, e);
    }

    /** @param {String} time */
    function removeEvent(time) {
      //console.log("Remove event " + time);
      var tableLink = document.getElementById("link" + time);
      tableLink.innerHTML = "";
    }

    /**
    * @param {String} newValue
    * @param {String} itemTime
    * */
    function changeEvent(newValue, itemTime) {
      //console.log("changeEvent", newValue, itemTime)
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
          removeEvent(e[1])
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
      onPartChecked();

      showEventButtons();
    }

    function onPartChecked() {
      // count how many are checked
      // if none or very few, hide the uncheck button
      var parts = $$("input:checkbox");
      var total = parts.length;
      var checked = parts.filter(":checked").length;
      if (checked > 1 && checked < total) {
        $$("#merge_button").show();
      } else {
        $$("#merge_button").hide();
      }
      if (checked > 0 ) {
        $$("#div_process").show();
        $$("#div_no_process").hide();
      } else {
        $$("#div_process").hide();
        $$("#div_no_process").show();
      }
    }



    function submitProcess() {
      //document.getElementById("upload_button").style.display = "none";
      document.getElementById("uploads_table").style.display = "block";

      var form = $$("#activity_form");
      $$.ajax({
        type: form.attr("method"),
        url: form.attr("action"),
        data: new FormData(form[0]),
        contentType: false,
        cache: false,
        processData: false,
        success: function(response) {
          showResults();
        },
      });
    }

    function submitEdit() {
      //document.getElementById("upload_button").style.display = "none";
      document.getElementById("uploads_table").style.display = "block";

      var form = $$("#activity_form");
      $$.ajax({
        type: form.attr("method"),
        url: "edit-activities",
        data: new FormData(form[0]),
        contentType: false,
        cache: false,
        processData: false,

        success: function(response) {
          var idElem = $$(response).find("id");
          if (idElem.length > 0) {
            window.location = "edit-activity?id=" + idElem.first().text().trim()
          }
        },
      });
    }

    function submitDownload() {
      var form = $$("#activity_form");

      var ajax = new XMLHttpRequest();
      ajax.open( "POST", "/download", true);
      ajax.responseType = 'blob';
      ajax.onload = function(e){
        download(e.target.response, ajax.getResponseHeader("Content-Disposition"), ajax.getResponseHeader("content-type"));
      };
      ajax.send(new FormData(form[0]))
    }

    // is tests current event state (as displayed on the page)
    function isCheckedLap(e) {
      return e[0] === "lap";
    }

    // was test original event state
    function wasUserLap(e) {
      return e[4] === "lap";
    }

    function wasLongPause(e) {
      return e[4].lastIndexOf("long pause") === 0;
    }

    function wasAnyPause(e) {
      return e[4] === "pause" || wasLongPause(e)
    }

    function wasSegment(e) {
      return e[4].lastIndexOf("segment") === 0 || e[4].lastIndexOf("private segment") === 0;
    }

    function wasHill(e) {
      return e[4] === "elevation";
    }

    function lapsClearAll() {
      events.forEach(function(e) {
        if (isCheckedLap(e)){
          changeEvent("", e[1]);
        }
      });
      onEventsChanged();
      showEventButtons();
    }

    function lapsSelectByPredicate(f) {
      events.forEach(function(e) {
        if (f(e)){
          changeEvent("lap", e[1]);
        }
      });
      onEventsChanged();
      showEventButtons();
    }

    function lapsSelectUser() {
      lapsSelectByPredicate(wasUserLap);
    }
    function lapsSelectLongPauses() {
      lapsSelectByPredicate(wasLongPause);
    }
    function lapsSelectAllPauses() {
      lapsSelectByPredicate(wasAnyPause);
    }

    /**
    @param {number} eventId
    */
    function selectMapEvent(eventId) {
       map.fire('popup', {feature: eventId});
    }

    function testPredicate(f) {
      var ret = false;
      events.forEach(function(e) {
        if (f(e)){
          ret = true;
        }
      });
      return ret;
    }
    function showEventButtons() {
      function showOrHide(name, func) {
        if (testPredicate(func)) {
          $$("#" + name).show();
        } else {
          $$("#" + name).hide();
        }
      }
      function enableOrDisable(name, func) {
        //showOrHide(name, func)
        $$("#" + name).prop("disabled", !testPredicate(func));
      }
      enableOrDisable("isCheckedLap", isCheckedLap);
      showOrHide("wasUserLap", wasUserLap);
      showOrHide("wasLongPause", wasLongPause);
      showOrHide("wasAnyPause", wasAnyPause);
      showOrHide("wasSegment", wasSegment);
      showOrHide("wasHill", wasHill);
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
     * @param {String} description description of the event
     * */
    function getSelectHtml(eTime, description) {
      var tableOption = document.getElementById(eTime);
      var html = tableOption.innerHTML;
      var value = tableOption.value;
      return description + '<br /><select onchange="changeEvent(this.options[this.selectedIndex].value,' + eTime + ')">' + html + '</select>';
    }
    function mapEventData(events, route) {
      var markers = [];

      function findPoint(route, time) {
        // interpolate between close points if necessary
        var i;
        for (i = 1; i < route.length - 1; i++) {
          // find first above
          if (route[i][2] >= time) break;

        }

        var prev = route[i-1];
        var next = route[i];

        var f;

        if (f < prev[2]) f = 0;
        else if (f > next[2]) f = 1;
        else f = (time - prev[2]) / (next[2] - prev[2]);

        function lerp(a, b, f) {
          return a + (b - a) * f;
        }
        return [
          lerp(prev[0], next[0], f),
          lerp(prev[1], next[1], f),
          lerp(prev[2], next[2], f), // should be time
          lerp(prev[3], next[3], f)
        ];
      }

      var dropStartEnd = events.slice(1, -1);

      dropStartEnd.forEach(function(e) {
        // ["split", 0, 0.0, "Run"]
        var r = findPoint(route, e[1]);

        if (r) {
          var marker = {
            "type": "Feature",
            "geometry": {
              "type": "Point",
              "coordinates": [r[0], r[1]]
            },
            "properties": {
              "title": e[0],
              "icon": "circle",
              "description": getSelectHtml(e[1], e[3]),
              "color": "#444",
              "opacity": 0.5
            }
          };

          markers.push(marker)
        } else {
          console.log("Point " + e[1] + " not found");
        }
      });
      return markers;
    }

    function renderEvents(events, route) {
      var markers = mapEventData(events, route);

      var routeLL = route.map(function(i){
        return [i[0], i[1]]
      });

      markers.unshift({
        "type": "Feature",
        "geometry": {
          "type": "Point",
          "coordinates": routeLL[0]
        },
        "properties": {
          "title": "Begin",
          "description": events[0][3],
          "icon": "triangle",
          "color": "#F22",
          "opacity": 1
        }
      });

      markers.push({
        "type": "Feature",
        "geometry": {
          "type": "Point",
          "coordinates": routeLL[routeLL.length-1]
        },
        "properties": {
          "title": "End",
          "description": events[events.length - 1][3],
          "icon": "circle",
          "color": "#2F2",
          "opacity": 0.5
        }
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

      // icon list see https://www.mapbox.com/maki-icons/ or see https://github.com/mapbox/mapbox-gl-styles/tree/master/sprites/basic-v9/_svg
      // basic geometric shapes, each also with - stroke variant:
      //   star, star-stroke, circle, circle-stroked, triangle, triangle-stroked, square, square-stroked
      //
      // specific, but generic enough:
      //   marker, cross, heart (Maki only?)
    }

    if (${activityData.hasGPS}) {
      var lat = ${activityData.lat};
      var lon = ${activityData.lon};
      mapboxgl.accessToken = "$mapBoxToken";
      var map = new mapboxgl.Map({
        container: 'map',
        style: 'mapbox://styles/ospanel/cjgpk2b1q001n2rt3vz2yvve2',
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

            map.on('popup', function (e) {
              var features = map.getSource("events")._data.features;

              if (e.feature >= 0 && e.feature < features.length) {
                var feature = features[e.feature];

                //var prev = map.getPopup();
                if (currentPopup) currentPopup.remove();

                // Populate the popup and set its coordinates
                // based on the feature found.
                var popup = new mapboxgl.Popup()
                    .setLngLat(feature.geometry.coordinates)
                    .setHTML(feature.properties.description)
                    .addTo(map);
                currentPopup = popup;
              }

            });
            map.on('click', function (e) {
              var features = map.queryRenderedFeatures(e.point, { layers: ['events'] });

              if (!features.length) {
                  return;
              }

              var feature = features[0];

              if (currentPopup) currentPopup.remove();
              // Populate the popup and set its coordinates
              // based on the feature found.
              var popup = new mapboxgl.Popup()
                  .setLngLat(feature.geometry.coordinates)
                  .setHTML(feature.properties.description)
                  .addTo(map);
              currentPopup = popup;
            });



          }

        };
        xmlHttp.open("GET", "route-data?id=" + encodeURIComponent(id), true); // true for asynchronous
        xmlHttp.send(null)});

    }

    """)
  }
}


object MergeAndEditActivity extends DefineRequest.Post("/merge-activity") {
  def saveAsNeeded(activityData: ActivityEvents)(implicit auth: StravaAuthResult) = {
    val prepare = activityData.cleanPositionErrors.processPausesAndEvents
    Storage.store(namespace.edit, prepare.id.id.filename, auth.userId, prepare.header, prepare)
    prepare
  }

  override def html(request: Request, resp: Response) = withAuth(request, resp) { implicit auth =>

    val fif = new DiskFileItemFactory()
    fif.setSizeThreshold(1 * 1024) // we do not expect any files, only form parts

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext

      def next() = items.next
    }

    val ops = itemsIterator.flatMap { item =>
      if (item.isFormField) {
        // expect field name id={FileId}
        val IdPattern = "id=(.*)".r
        val id = item.getFieldName match {
          case IdPattern(idText) =>
            Some(FileId.parse(idText))
          case _ =>
            None
        }
        /*
        //println(item)
        val is = item.openStream()
        val itemContent = try {
          IOUtils.toString(is)
        } finally {
          is.close()
        }
        */
        id
      } else {
        None
      }
    }.toVector

    // TODO: create groups, process each group separately
    val toMerge = ops.flatMap { op =>
      Storage.load[ActivityHeader, ActivityEvents](Storage.getFullName(namespace.stage, op.filename, auth.userId)).map(_._2.applyFilters(auth))
    }


    if (toMerge.nonEmpty) {
      val activityData = saveAsNeeded(toMerge.reduceLeft(_ merge _))

      // report back the edited ID
      <activity>
        <id>
          {activityData.id.id}
        </id>
      </activity>
    }
    else {
      <activity>
        <none>
        </none>
      </activity>
    }
  }

}


object EditActivity extends DefineRequest("/edit-activity") with ActivityRequestHandler {
  override def html(req: Request, resp: Response) = withAuth(req, resp ){ implicit auth =>

    val session = req.session()
    val id = req.queryParams("id")
    val actId = FileId.parse(id)

    Storage.load2nd[Main.ActivityEvents](Storage.getFullName(Main.namespace.edit, actId.filename, auth.userId)).map { activityData =>
      val content = activityHtmlContent(actId, activityData, session, resp)
      <html>
        <head>
          <title>{shared.appName}</title>{headPrefix}{content.head}
        </head>
        <body>
          {bodyHeader(auth)}{activityData.id.hrefLink}{content.body}{bodyFooter}
        </body>
      </html>

    }.getOrElse {
      <html>
        <head>
          <title>{shared.appName}</title>{headPrefix}
        </head>
        <body>
          {bodyHeader(auth)}
            No activity data
          {bodyFooter}
        </body>
      </html>

    }



  }
}
