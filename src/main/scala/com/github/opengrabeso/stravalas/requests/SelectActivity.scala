package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}
import DateTimeOps._
import FileId._

object SelectActivity extends DefineRequest("/selectActivity") {

  object ActivityAction extends Enumeration {
    type ActivityAction = Value
    val ActUpload, ActMerge, ActIgnore = Value
  }

  import ActivityAction._

  val displayActivityAction = Map(
    ActUpload -> "Upload",
    ActMerge -> "Merge with above",
    ActIgnore -> "Ignore"
  )

  def htmlActivityAction(id: FileId, types: Seq[ActivityAction], action: ActivityAction) = {
    val idString = id.toString
    <select id={idString} name="do" onchange={s"changeActivity(this, this.options[this.selectedIndex].value, '$idString')"}>
      {for (et <- types) yield {
      <option value={et.id.toString} selected={if (action == et) "" else null}>
        {displayActivityAction(et)}
      </option>
    }}
    </select>
  }


  def sameActivity(events: Main.ActivityEvents, r: Main.ActivityEvents) = ???

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val stravaActivities = Main.recentStravaActivities(auth)

    // ignore anything older than oldest of recent Strava activities
    val ignoreBefore = stravaActivities.lastOption.map(_.startTime)

    val stagedActivities = Main.stagedActivities(auth).toVector // toVector to avoid debugging streams

    val recentActivities = ignoreBefore.fold(stagedActivities) { before =>
      stagedActivities.filter(_.id.startTime > before)
    }.sortBy(_.id.startTime)

    // match recent activities against Strava activities
    // a significant overlap means a match
    val recentToStrava = recentActivities.map { r =>
      r -> stravaActivities.find(_ isMatching r.id)
    }

    // detect activity groups - any overlapping activities should be one group, unless
    //val activityGroups =

    val actions = ActivityAction.values.toSeq
    var ignored = false
    <html>
      <head>
        {/* allow referer when using redirect to unsafe getSuunto page */}
        <meta name="referrer" content="unsafe-url"/>
        {headPrefix}<title>Stravamat - select activity</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
      </head>
      <body>
        {bodyHeader(auth)}<h2>Staging</h2>

        <table class="activities">
          {for ((actEvents, actStrava) <- recentToStrava) yield {
            val act = actEvents.id
            // once any activity is present on Strava, do not offer upload by default any more
            // (if some earlier is not present, it was probably already uploaded and deleted)
            if (actStrava.isDefined) ignored = true
            val action = if (ignored) ActIgnore else ActUpload
            <tr>
              <td>{Main.localeDateRange(act.startTime, act.endTime)}</td>
              <td>{act.sportName}</td>
              <td>{if (actEvents.hasGPS) "GPS" else "--"}</td>
              <td>{act.hrefLink}</td>
              <td>{Main.displayDistance(act.distance)} km</td>
              <td>{Main.displaySeconds(act.duration)}</td>
              <td>
                <form action="activity" method="get">
                  <input type="hidden" name="activityId" value={act.id.toString}/>
                  <input type="submit" value=">>"/>
                </form>
              </td>
              <td>{htmlActivityAction(act.id, actions, action)}</td>
              <td>{actStrava.fold(<p>{act.id.toString}</p>)(_.hrefLink)}</td>
            </tr>
        }}
        </table>
        <hr/>
        <h2>Data sources</h2>
        <a href="loadFromStrava">Load from Strava ...</a>
        {
          /* getSuunto is peforming cross site requests to the local browser, this cannot be done on a secure page */

          val sincePar = ignoreBefore.fold("")("?since=" + _.toString)
          val getSuuntoLink = s"window.location.assign(unsafe('getSuunto$sincePar'))"
          <a href="javascript:;" onClick={getSuuntoLink}>Get from Suunto devices ...</a>
        }

        <form action="upload" method="post" enctype="multipart/form-data">
          <p>Select files to upload

            <div id="drop-container" style="border:1px solid black;height:100px;">
              Drop Here
            </div>

            <input type="file" id="fileInput" name="activities" multiple="multiple" accept=".fit,.gpx,.tcx,.sml,.xml"/>
          </p>
          <input type="submit" value="Upload"/>
        </form>
        {bodyFooter}
        <script>{xml.Unparsed(
        //language=JavaScript
          """

          // dragover and dragenter events need to have 'preventDefault' called
          // in order for the 'drop' event to register.
          // See: https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Drag_operations#droptargets
          var dropContainer = document.getElementById("drop-container");

          dropContainer.addEventListener("dragover", function (evt){
            evt.preventDefault();
          });
          dropContainer.addEventListener("dragenter", function (evt){
            evt.preventDefault();
          });
          dropContainer.addEventListener("drop", function (evt){
            // pretty simple -- but not for IE :(
            fileInput.files = evt.dataTransfer.files;
            evt.preventDefault();
          });


          function changeActivity(event, value, id) {
            console.log("changeActivity " + event + ",");
          }

          function unsafe(uri) {
              var abs = window.location.href;
              var http = abs.replace(/^https:/, 'http:');
              var rel = http.lastIndexOf('/');
              return http.substring(0, rel + 1) + uri;
          }
          """
        )}
        </script>
      </body>
    </html>
  }

}