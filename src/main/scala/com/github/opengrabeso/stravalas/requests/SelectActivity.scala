package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}
import DateTimeOps._

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

  def htmlActivityAction(id: Long, types: Seq[ActivityAction], action: ActivityAction) = {
    <select id={id.toString} name="do" onchange={s"changeActivity(this, this.options[this.selectedIndex].value, $id)"}>
      {for (et <- types) yield {
      <option value={et.id.toString} selected={if (action == et) "" else null}>
        {displayActivityAction(et)}
      </option>
    }}
    </select>
  }


  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val activities = Main.stagedActivities(auth).sortBy(_.id.startTime)

    val actions = ActivityAction.values.toSeq

    // detect activity groups - any overlapping activities should be one group, unless
    //val activityGroups =


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
          {for (actEvents <- activities) yield {
            val act = actEvents.id
            <tr>
              <td>{Main.localeDateRange(act.startTime, act.endTime)}</td>
              <td> {act.sportName} </td>
              <td> {if (actEvents.hasGPS) "GPS" else "--"}</td>
              <td> <a href={act.link}> {act.name} </a> </td>
              <td>{Main.displayDistance(act.distance)} km</td>
              <td>{Main.displaySeconds(act.duration)}</td>
              <td>
                <form action="activity" method="get">
                  <input type="hidden" name="activityId" value={act.id.toString}/>
                  <input type="submit" value=">>"/>
                </form>
              </td>
              <td>{htmlActivityAction(act.id, actions, ActUpload)}</td>
              <td> {act.id} </td>
            </tr>
        }}
        </table>
        <hr/>
        <h2>Data sources</h2>
        <a href="loadFromStrava">Load from Strava ...</a>
        {/* getSuunto is peforming cross site requests to the local browser, this cannot be done on a secure page */}
        <a href="javascript:;" onClick="window.location.assign(unsafe('getSuunto'))">Get from Suunto devices ...</a>

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