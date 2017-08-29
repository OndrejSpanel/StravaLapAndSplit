package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}
import shared.Util._
import Main._
import org.joda.time.{DateTime => ZonedDateTime}

import scala.xml.NodeSeq

abstract class SelectActivity(name: String) extends DefineRequest(name) {

  def title: String
  def sources(before: ZonedDateTime): NodeSeq
  def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = true
  def ignoreBefore(stravaActivities: Seq[ActivityId]): ZonedDateTime

  def htmlActivityAction(id: FileId, include: Boolean) = {
    val idString = id.toString
    <input type="checkbox" name={s"id=$idString"} checked={if (include) "true" else null}></input>
  }

  def jsResult(func: String) = {

    val toRun = s"function () {return $func}()"

    <script>document.write({xml.Unparsed(toRun)})</script>
  }


  def uploadResultsHtml() = {
    <div id="uploads_table" style="display: none;">
      <table id="uploaded"></table>
      <h4 id="uploads_process">Processing...</h4>
      <h4 id="uploads_progress" style="display: none;">Uploading ...</h4>
      <h4 id="uploads_complete" style="display: none;">Complete</h4>
    </div>
    <script>
      {xml.Unparsed(
      // language=JavaScript
      """
    function extractResult(node, tagName, callback) {
      var n = node.getElementsByTagName(tagName);
      if (n.length > 0) return callback(n[0].textContent);
    }
    function addRow(tableBody, text) {
      var tr = document.createElement('TR');
      var td = document.createElement('TD');
      td.innerHTML = text;
      tr.appendChild(td);
      tableBody.appendChild(tr);
    }
    function showProgress() {
      $("#uploads_process").hide();
      $("#uploads_progress").show();
      var p = $("#uploads_progress");
      p.text(p.text() + ".");
      p.show();
    }
    function showResults() {

      ajaxAsync("check-upload-status", "", function(response) {
        var results = response.documentElement.getElementsByTagName("result");
        var complete = response.documentElement.getElementsByTagName("complete");
        var tableBody = document.getElementById("uploaded");
        for (var i = 0; i < results.length; i++) {

          var res = extractResult(results[i], "done", function(text) {
            // TODO: get Strava user friendly name, or include a time?
            return "Done <a href=https://www.strava.com/activities/" + text + ">" + text + "</a>";
          }) || extractResult(results[i], "duplicate", function(text) {
            if (text ==0) {
              return "Duplicate";
            } else {
              return "Duplicate of <a href=https://www.strava.com/activities/" + text + ">" + text + "</a>";
            }
          }) || extractResult(results[i], "error", function(text) {
            return "Error " + text;
          });
          if (res) addRow(tableBody, res);
        }
        if (complete.length == 0) {
          showProgress();
          setTimeout(showResults, 1000);
        } else {
          $("#uploads_complete").show();
          $("#uploads_process").hide();
          $("#uploads_progress").hide();
        }
      }, function (failure) {
        console.log(failure);
        setTimeout(showResults, 1000);
      });
    }

    """)}
    </script>
  }

  override def html(request: Request, resp: Response) = {
    withAuth(request, resp) { auth =>

      val session = request.session()

      val stravaActivities = recentStravaActivities(auth)

      // Strava upload progress session id
      val sid = System.currentTimeMillis().toString
      session.attribute("sid", sid)

      val stagedActivities = Main.stagedActivities(auth).toVector // toVector to avoid debugging streams
      val before = ignoreBefore(stravaActivities)

      //println(s"Ignore before $before")

      val recentActivities = stagedActivities.filter(_.id.startTime > before).sortBy(_.id.startTime)
      //println(s"Staged ${stagedActivities.mkString("\n  ")}")
      //println(s"Recent ${recentActivities.mkString("\n  ")}")

      // match recent activities against Strava activities
      // a significant overlap means a match
      val recentToStrava = recentActivities.map { r =>
        r -> stravaActivities.find(_ isMatching r.id)
      }.filter((filterListed _).tupled)

      <html>
        <head>
          {headPrefix}<title>Stravamat -
          {title}
        </title>
          <style>
            tr.activities:nth-child(even) {{background-color: #f2f2f2}}
            tr.activities:hover {{background-color: #f0f0e0}}
          </style>
          <script src="static/ajaxUtils.js"></script>
          <script src="static/timeUtils.js"></script>
          <script src="static/jquery-3.2.1.min.js"></script>
          <script>
            {xml.Unparsed(
            //language=JavaScript
            """
            function submitProcess() {
              document.getElementById("upload_button").style.display = "none";
              document.getElementById("uploads_table").style.display = "block";

              var form = $("#process-form");
              $.ajax({
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

            function submitDelete() {
              var form = $("#process-form");
              $.ajax({
                type: "post",
                url: "delete-selected",
                data: new FormData(form[0]),
                contentType: false,
                cache: false,
                processData: false,
                success: function(response) {
                    // response is XML id code of the activity to redirect to

                    window.location.reload(false);
                },
              });
            }

            function submitEdit() {
              var form = $("#process-form");
              $.ajax({
                type: "post",
                url: "merge-activity",
                data: new FormData(form[0]),
                contentType: false,
                cache: false,
                processData: false,
                success: function(response) {
                  var idElem = $(response).find("id");
                  if (idElem.length > 0) {
                    window.location = "edit-activity?id=" + idElem.first().text().trim()
                  }
                },
              });
            }
            """
          )}

          </script>
          </head>
          <body>
            {bodyHeader(auth)}{sources(before)}<h2>Activities</h2>
            <form id="process-form" action="process" method="post" enctype="multipart/form-data">
              <table class="activities">
                {// find most recent Strava activity
                val mostRecentStrava = stravaActivities.headOption.map(_.startTime)

                for ((actEvents, actStrava) <- recentToStrava) yield {
                  //println(s"  act $actEvents $actStrava")
                  val act = actEvents.id
                  val ignored = mostRecentStrava.exists(_ > act.endTime)
                  // once any activity is present on Strava, do not offer upload by default any more
                  // (if some earlier is not present, it was probably already uploaded and deleted)
                  <tr>
                    <td>
                      {
                      val detected = Main.detectSportBySpeed(actEvents.stats, act.sportName)
                      if (act.sportName == Event.Sport.Workout) {
                        s"$detected?"
                      } else if (act.sportName != detected) {
                        s"${act.sportName}->$detected"
                      } else act.sportName
                      //println(s"    $detected")
                      }
                    </td>
                    <td>{if (actEvents.hasGPS) "GPS" else "--"}</td>
                    <td>{if (actEvents.hasAttributes) "Rec" else "--"}</td>
                    <td>{act.hrefLink}</td>
                    <td>{displayDistance(act.distance)} km</td>
                    <td>{displaySeconds(act.duration)}</td>
                    <td>{htmlActivityAction(act.id, !ignored)}</td>
                    <td>{actStrava.map(_.hrefLink).getOrElse(NodeSeq.Empty)}</td>
                    <td>{act.id.toReadableString}</td>
                  </tr>
                }}
              </table>

          </form>
          <button id="upload_button" onclick="submitProcess()">Process...</button>
          {uploadResultsHtml()}
          <button onclick="submitDelete()">Delete from Stravamat</button>
          <button onclick="submitEdit()">Merge and edit...</button>
          {bodyFooter}
          <script>{xml.Unparsed(
          //language=JavaScript
          """
          $("#process-form").submit(function(event) {
            // Stop the browser from submitting the form.
            event.preventDefault();
          });
          """)}
        </script>
        </body>
      </html>
    }
  }
}