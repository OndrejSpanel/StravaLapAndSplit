package com.github.opengrabeso.mixtio
package requests

import Main._
import common.model._
import java.time.ZonedDateTime
import spark.{Request, Response, Session}
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload

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

      <script src='https://api.mapbox.com/mapbox-gl-js/v1.0.0/mapbox-gl.js'></script>
      <link href='https://api.mapbox.com/mapbox-gl-js/v1.0.0/mapbox-gl.css' rel='stylesheet' />

      <link rel="stylesheet" type="text/css" href="static/activityPage.css"/>
      <link rel="stylesheet" type="text/css" href="static/page.css"/>

      <script src="frontend/script"></script>
      <script src="frontend/dependencies"></script>
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
                <td> {xml.Unparsed(Main.htmlDescription(t))} </td>
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

      function activityEvents() {
        return [
          ${activityData.editableEvents.mkString("[", "],\n        [", "]")}
        ];
      }

      function actIdName() {
        return "$actIdName";
      }

      var id = actIdName();
      // events are: ["split", 0, 0.0, "Run", "lap", "Start"] - kind, time, distance, sport, original kind, description
      var events = activityEvents();

      // callback, should update the map when events are changed
      var onEventsChanged = function() {};
      var currentPopup = undefined;


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

    function lerp(a, b, f) {
      return a + (b - a) * f;
    }

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

      return [
        lerp(prev[0], next[0], f),
        lerp(prev[1], next[1], f),
        lerp(prev[2], next[2], f), // should be time
        lerp(prev[3], next[3], f)
      ];
    }

    function mapEventData(events, route) {
      var markers = [];
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



    function generateGrid(bounds, size, fixedPoint) {
        // TODO: pad the bounds to make sure we draw the lines a little longer

        var grid_box = bounds;
        var avg_y = (grid_box._ne.lat + grid_box._sw.lat) * 0.5;

        // Meridian length is always the same
        var meridian = 20003930.0;
        var equator = 40075160;
        var parallel = Math.cos(avg_y * Math.PI / 180) * equator;

        var grid_distance = 1000.0;

        var grid_step_x = grid_distance / parallel * 360;
        var grid_step_y = grid_distance / meridian * 180;

        var minSize = Math.max(size.x, size.y);
        var minLineDistance = 10;
        var maxLines = minSize / minLineDistance;

        var latLines = _latLines(bounds, fixedPoint, grid_step_y, maxLines);
        var lngLines = _lngLines(bounds, fixedPoint, grid_step_x, maxLines);
        var alpha = Math.min(latLines.alpha, lngLines.alpha);

        if (latLines.lines.length > 0 && lngLines.lines.length > 0) {
            var grid = [];
            var i;
            for (i in latLines.lines) {
                if (Math.abs(latLines[i]) > 90) {
                    continue;
                }
                grid.push(_horizontalLine(bounds, latLines.lines[i], alpha));
            }

            for (i in lngLines.lines) {
                grid.push(_verticalLine(bounds, lngLines.lines[i], alpha));
            }
            return [grid, alpha];
        }
        return [[], 0];
    }

    function _latLines(bounds, fixedPoint, yticks, maxLines) {
        return _lines(
            bounds._sw.lat,
            bounds._ne.lat,
            yticks, maxLines, fixedPoint[1]
        );
    }
    function _lngLines(bounds, fixedPoint, xticks, maxLines) {
        return _lines(
            bounds._sw.lng,
            bounds._ne.lng,
            xticks, maxLines, fixedPoint[0]
        );
    }

    function _lines(low, high, ticks, maxLines, fixedCoord) {
        var delta = high - low;

        var lowAligned = Math.floor((low - fixedCoord)/ ticks) * ticks + fixedCoord;

        var lines = [];

        if ( delta / ticks <= maxLines) {
            for (var i = lowAligned; i <= high; i += ticks) {
                lines.push(i);
            }
        }
        var aScale = 15;
        var a = ( maxLines / aScale) / (delta / ticks);
        return {
            lines: lines,
            alpha: Math.min(1, Math.sqrt(a))
        };
    }

    function _verticalLine(bounds, lng, alpha) {
        return [
            [lng, bounds.getNorth()],
            [lng, bounds.getSouth()]
        ];
    }
    function _horizontalLine(bounds, lat, alpha) {
        return [
            [bounds.getWest(), lat],
            [bounds.getEast(), lat]
        ];
    }


    function renderGrid(fixedPoint) {
       var size = {
        x: map.getContainer().clientWidth,
        y: map.getContainer().clientHeight
      };
      var gridAndAlpha = generateGrid(map.getBounds(), size, fixedPoint);
      var grid = gridAndAlpha[0];
      var alpha = gridAndAlpha[1];

      var gridData = {
        "type": "Feature",
        "properties": {},
        "geometry": {
          "type": "MultiLineString",
          "coordinates": grid
        }
      };

      var existing = map.getSource('grid');
      if (existing) {
        existing.setData(gridData);
        map.setPaintProperty('grid', 'line-opacity', alpha);
        map.setLayoutProperty('grid', 'visibility', alpha > 0 ? 'visible' : 'none')
      } else {
        map.addSource("grid", {
          "type": "geojson",
          "data": gridData
        });
        map.addLayer({
          "id": "grid",
          "type": "line",
          "source": "grid",
          "layout": {
            "line-join": "round",
            "line-cap": "round"
          },
          "paint": {
            "line-color": "#e40",
            "line-width": 2,
            'line-opacity': alpha
          }
        });
      }

      // icon list see https://www.mapbox.com/maki-icons/ or see https://github.com/mapbox/mapbox-gl-styles/tree/master/sprites/basic-v9/_svg
      // basic geometric shapes, each also with - stroke variant:
      //   star, star-stroke, circle, circle-stroked, triangle, triangle-stroked, square, square-stroked
      //
      // specific, but generic enough:
      //   marker, cross, heart (Maki only?)
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
        style: 'mapbox://styles/ospanel/cjkbfwccz11972rmt4xvmvme6',
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
            renderGrid(route[0]);

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

            var moveHandler = function (e){
              var existing = map.getSource('events');
              if (existing) {
                var data = existing._data;
                renderGrid(data.features[0].geometry.coordinates);
              }
            };
            map.on('moveend', moveHandler);
            map.on('move', moveHandler);
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
    // TODO: make sure edited name is unique
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
        import org.apache.commons.io.IOUtils
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
      // first merge all GPS data
      // then merge in all attribute data
      val (toMergeGPS, toMergeAttrRaw) = toMerge.partition(_.hasGPS)
      val timeOffset = Settings(auth.userId).questTimeOffset
      val toMergeAttr = toMergeAttrRaw.map(_.timeOffset(-timeOffset))

      val merged = if (toMergeGPS.nonEmpty) {
        val gpsMerged = toMergeGPS.reduceLeft(_ merge _)
        (gpsMerged +: toMergeAttr).reduceLeft(_ merge _)
      } else {
        toMerge.reduceLeft(_ merge _)
      }
      val activityData = saveAsNeeded(merged)

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
          <title>{appName}</title>{headPrefix}{content.head}
        </head>
        <body>
          {bodyHeader(auth)}{hrefLink(activityData.id)}{content.body}{bodyFooter}
        </body>
      </html>

    }.getOrElse {
      <html>
        <head>
          <title>{appName}</title>{headPrefix}
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
