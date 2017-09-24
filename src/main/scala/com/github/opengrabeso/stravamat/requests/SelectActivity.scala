package com.github.opengrabeso.stravamat
package requests

import shared.Util._
import Main._
import org.joda.time.{DateTime => ZonedDateTime}

import scala.xml.NodeSeq

trait SelectActivityPart extends HtmlPart with ShowPending with UploadResults with ActivitiesTable {
  def title: String

  def sources(before: ZonedDateTime): NodeSeq
  def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = true
  def ignoreBefore(stravaActivities: Seq[ActivityId]): ZonedDateTime

  def htmlActivityAction(id: FileId, include: Boolean) = {
    val idString = id.toString
    <input type="checkbox" name={s"id=$idString"} checked={if (include) "true" else null}></input>
  }

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    headPrefix ++
    <title>
      Stravamat - {title}
    </title>
    <script src="static/ajaxUtils.js"></script>
    <script src="static/timeUtils.js"></script>
    <script src="static/jquery-3.2.1.min.js"></script> ++
    super.headerPart(req, auth)

  }

  abstract override def bodyPart(request: Request, auth: StravaAuthResult): NodeSeq = {
    val session = request.session()

    val stravaActivities = recentStravaActivities(auth)

    startUploadSession(session)

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

    super.bodyPart(request, auth) ++
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
          showPending();
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
            error: function() {hidePending()}
          });
        }

        function submitEdit() {
          showPending();
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
              } else {
                hidePending();
              }
            },
            error: function() {hidePending()}
          });
        }
        """
    )}
    </script> ++
      sources(before) ++ <h2>Activities</h2>
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
              <td>{htmlActivityAction(act.id, !ignored)}</td>
              <td>{jsResult(jsDateRange(act.startTime, act.endTime))}</td>
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
              <td>{displayDistance(act.distance)}</td>
              <td>{displaySeconds(act.duration)}</td>
              <td>{actStrava.map(_.hrefLink).getOrElse(NodeSeq.Empty)}</td>
              <td>{actEvents.describeData}</td>
              <td>{act.hrefLink}</td>
            </tr>
          }}
        </table>

      </form>
      <button id="upload_button" onclick="submitProcess()">Process...</button>
      <button onclick="submitDelete()">Delete from Stravamat</button>
      <button onclick="submitEdit()">Merge and edit...</button> ++
      uploadResultsHtml() ++
      <script>{xml.Unparsed(
        //language=JavaScript
        """
      $("#process-form").submit(function(event) {
        // Stop the browser from submitting the form.
        event.preventDefault();
      });
      """)}
      </script>
  }

}

abstract class SelectActivity(name: String) extends DefineRequest(name) with HtmlByParts with SelectActivityPart with Headers
