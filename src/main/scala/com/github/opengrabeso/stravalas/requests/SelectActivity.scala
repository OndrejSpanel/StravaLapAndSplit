package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}
import DateTimeOps._
import org.joda.time.{Seconds, DateTime => ZonedDateTime}
import net.suunto3rdparty.Settings

import scala.xml.NodeSeq

abstract class SelectActivity(name: String) extends DefineRequest(name) {

  def title: String
  def sources(before: ZonedDateTime): NodeSeq

  def htmlActivityAction(id: FileId, include: Boolean) = {
    val idString = id.toString
    <input type="checkbox" name={s"id=$idString"} checked={if (include) "true" else null}></input>
  }

  def jsResult(func: String) = {

    val toRun = s"function () {return $func}()"

    <script>document.write({xml.Unparsed(toRun)})</script>
  }

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val stravaActivities = Main.recentStravaActivities(auth)
    val sid: java.lang.Long = System.currentTimeMillis()
    session.attribute("sid", sid)

    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeLast = stravaActivities.lastOption.map(_.startTime)
    val ignoreBeforeFirst = stravaActivities.headOption.map(_.startTime minusDays  14)
    val ignoreBeforeNow = new ZonedDateTime() minusMonths 2

    val before = (Seq(ignoreBeforeNow) ++ ignoreBeforeLast ++ ignoreBeforeFirst).max

    val stagedActivities = Main.stagedActivities(auth).toVector // toVector to avoid debugging streams

    val recentActivities = stagedActivities.filter(_.id.startTime > before).sortBy(_.id.startTime)

    // match recent activities against Strava activities
    // a significant overlap means a match
    val recentToStrava = recentActivities.map { r =>
      r -> stravaActivities.find(_ isMatching r.id)
    }

    // detect activity groups - any overlapping activities should be one group, unless
    //val activityGroups =

    val settings = Settings(auth.userId)
    <html>
      <head>
        {/* allow referer when using redirect to unsafe getSuunto page */}
        <meta name="referrer" content="unsafe-url"/>
        {headPrefix}<title>Stravamat - {title}</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
        <script src="static/ajaxUtils.js"></script>
        <script src="static/jquery-3.2.1.min.js"></script>
        <script>{xml.Unparsed(
          //language=JavaScript
          """
          /** @return {string} */
          function getLocale() {
            return navigator.languages[0] || navigator.language;
          }
          /**
          * @param {string} t
          * @return {string}
          */
          function formatDateTime(t) {
            var locale = getLocale();
            var date = new Date(t);
            return new Intl.DateTimeFormat(
              locale,
              {
                year: "numeric",
                month: "numeric",
                day: "numeric",
                hour: "numeric",
                minute: "numeric",
              }
            ).format(date)
          }
          /**
          * @param {string} t
          * @return {string}
          */
          function formatTime(t) {
            var locale = getLocale();
            var date = new Date(t);
            return new Intl.DateTimeFormat(
              locale,
              {
                //year: "numeric",
                //month: "numeric",
                //day: "numeric",
                hour: "numeric",
                minute: "numeric",
              }
            ).format(date)
          }
          function unsafe(uri) {
              var abs = window.location.href;
              var http = abs.replace(/^https:/, 'http:');
              var rel = http.lastIndexOf('/');
              return http.substring(0, rel + 1) + uri;
          }
          function formatTimeSec(t) {
            var locale = getLocale();
            var date = new Date(t);
            return new Intl.DateTimeFormat(
              locale,
              {
                //year: "numeric",
                //month: "numeric",
                //day: "numeric",
                hour: "numeric",
                minute: "numeric",
                second: "numeric"
              }
            ).format(date)
          }
          function currentQuestTime(d) {
            var offset = parseInt(document.getElementById("quest_time_offset").value);
            return formatTimeSec(new Date(d.getTime() + 1000*offset));
          }
          function updateClock() {
            var d = new Date();
            document.getElementById("time").innerHTML = formatTimeSec(d);
            document.getElementById("timeQuest").innerHTML = currentQuestTime(d);
            setTimeout(function () {
              updateClock();
            }, 1000);
          }
          function settingsChanged() {
            // send the new settings to the server
            var questOffset = parseInt(document.getElementById("quest_time_offset").value);
            var maxHR = parseInt(document.getElementById("max_hr").value);
            ajaxAsync("save-settings?quest_time_offset=" + questOffset + "&max_hr=" + maxHR, "", function(response) {});

          }
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
                  console.log("AJAX submitted");
              },
            });

          }

          """
        )}

        </script>
      </head>
      <body>
        {bodyHeader(auth)}

        {sources(before)}

        <h2>Settings</h2>
        <table>
          <tr><td>
            Max HR</td><td><input type="number" name="max_hr" id="max_hr" min="100" max="260" value={settings.maxHR.toString} onchange="settingsChanged()"></input>
          </td></tr>
          <tr><td>
            Quest time offset</td><td> <input type="number" id="quest_time_offset" name="quest_time_offset" min="-60" max="60" value={settings.questTimeOffset.toString} onchange="settingsChanged()"></input>
          </td>
            <td>Adjust up or down so that Quest time below matches the time on your watch</td>
          </tr>

          <tr>
            <td>Current time</td>
            <td id="time"></td>
          </tr>
          <tr>
            <td>Quest time</td>
            <td><b id="timeQuest"></b></td>
          </tr>
        </table>



        <h2>Staging</h2>
        <form id="process-form" action="process" method="post" enctype="multipart/form-data">
          <table class="activities">
            {
              // find most recent Strava activity
              val mostRecentStrava = stravaActivities.headOption.map(_.startTime)

              for ((actEvents, actStrava) <- recentToStrava) yield {
                val act = actEvents.id
                val ignored = mostRecentStrava.exists(_ > actEvents.id.endTime)
                // once any activity is present on Strava, do not offer upload by default any more
                // (if some earlier is not present, it was probably already uploaded and deleted)
                <tr>
                  <td><button onclick={s"ajaxAction('delete?id=${act.id.toString}');return false"}>Unstage</button></td>
                  <td>{jsResult(Main.jsDateRange(act.startTime, act.endTime))}</td>
                  <td>{act.sportName}</td>
                  <td>{if (actEvents.hasGPS) "GPS" else "--"}</td>
                  <td>{if (actEvents.hasAttributes) "Rec" else "--"}</td>
                  <td>{act.hrefLink}</td>
                  <td>{Main.displayDistance(act.distance)} km</td>
                  <td>{Main.displaySeconds(act.duration)}</td>
                  <td>{htmlActivityAction(act.id, !ignored)}</td>
                  <td>{actStrava.fold(<div>{act.id.toString}</div>)(_.hrefLink)}</td>
                </tr>
              }
            }
          </table>
          <input id="upload_button" type="submit" value="Process..."/>

          <div id="uploads_table" style="display: none;">
            {Process.uploadResultsHtml()}
          </div>
        </form>
        {bodyFooter}
        <script>{xml.Unparsed(
          //language=JavaScript
          """
          $("#process-form").submit(function(event) {
            // Stop the browser from submitting the form.
            event.preventDefault();
            submitProcess();
          });

          updateClock()
          """)}
        </script>
      </body>
    </html>
  }

}