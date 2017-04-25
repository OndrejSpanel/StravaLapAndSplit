package com.github.opengrabeso.stravalas
package requests

import javax.servlet.http.HttpServletResponse

import spark.{Request, Response}

import scala.util.Try

object SelectActivity extends DefineRequest("/selectActivity") {
  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val activities = Main.stagedActivities(auth)
    <html>
      <head>
        {headPrefix}<title>Stravamat - select activity</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
      </head>
      <body>
        {bodyHeader(auth)}<h2>Staging</h2>

        <table class="activities">
          {for (act <- activities) yield {
          <tr>
            <td>
              {act.id}
            </td> <td>
            {act.sportName}
          </td> <td>
            <a href={act.link}>
              {act.name}
            </a>
          </td>
            <td>
              {Main.displayDistance(act.distance)}
              km</td> <td>
            {Main.displaySeconds(act.duration)}
          </td>
            <td>
              <form action="activity" method="get">
                <input type="hidden" name="activityId" value={act.id.toString}/>
                <input type="submit" value=">>"/>
              </form>
            </td>
          </tr>
        }}
        </table>
        <hr/>
        <h2>Data sources</h2>
        <a href={s"loadFromStrava"}>Load from Strava ...</a>

        <form action="upload" method="post" enctype="multipart/form-data">
          <p>Select files to upload

            <div id="drop-container" style="border:1px solid black;height:100px;">
              Drop Here
            </div>

            <input type="file" id="fileInput" name="activities" multiple="multiple" accept=".fit,.gpx,.tcx,.sml,.xml"/>
          </p>
          <input type="submit" value="Upload"/>
        </form>{bodyFooter}{xml.Unparsed(
        """
                 <script>
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
                 </script>
              """
      )}
      </body>
    </html>
  }

}