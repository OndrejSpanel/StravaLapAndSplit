package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}
import DateTimeOps._

object GetFiles extends DefineRequest("/getFiles") {

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

    var ignored = false
    <html>
      <head>
        {headPrefix}<title>Stravamat - upload files</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
        <script src="static/ajaxUtils.js"></script>
      </head>
      <body>
        {bodyHeader(auth)}<h2>Staging</h2>

        <form action="upload" method="post" enctype="multipart/form-data">
          <input type="hidden" id="timezone" name="timezone" value=""></input>
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

          document.getElementById("timezone").value = Intl.DateTimeFormat().resolvedOptions().timeZone

          """
        )}
        </script>
      </body>
    </html>
  }

}