package com.github.opengrabeso.mixtio
package requests

import shared._
import common.Util._
import Main._
import common.model._
import com.google.appengine.api.taskqueue.{QueueFactory, TaskOptions}
import java.time.ZonedDateTime

import scala.xml.NodeSeq

trait SelectActivityPart extends HtmlPart with ShowPending with UploadResults with ActivitiesTable {
  def title: String

  def sources(before: ZonedDateTime): NodeSeq
  def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = true
  def ignoreBefore(stravaActivities: Seq[ActivityId]): ZonedDateTime

  def htmlActivityAction(id: FileId, include: Boolean) = {
    val idString = id.toString
    <input class="checkSelect" type="checkbox" name={s"id=$idString"} checked={if (include) "true" else null} onchange="selectChecked(this)"></input>
  }

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    headPrefix ++
    <title>
      {appName} - {title}
    </title>
    <script src="static/ajaxUtils.js"></script>
    <script src="static/timeUtils.js"></script>
    <script src="static/jquery-3.2.1.min.js"></script> ++
    super.headerPart(req, auth)

  }

  abstract override def bodyPart(request: Request, auth: StravaAuthResult): NodeSeq = {

    val timing = Timing.start(true)

    val session = request.session()

    val (stravaActivities, oldStravaActivities) = recentStravaActivitiesHistory(auth, 2)

    val neverBefore = common.ActivityTime.alwaysIgnoreBefore(stravaActivities)

    startUploadSession(session)

    val notBefore = ignoreBefore(stravaActivities)
    val stagedActivities = Main.stagedActivities(auth, notBefore)

    //println(s"Ignore before $notBefore")



    def findMatchingStrava(ids: Seq[ActivityHeader], strava: Seq[ActivityId]): Seq[(ActivityHeader, Option[ActivityId])] = {
      ids.map( a => a -> strava.find(_ isMatching a.id))
    }
    // never display any activity which should be cleaned by UserCleanup
    val oldStagedActivities = stagedActivities.filter(_.id.startTime < neverBefore)
    val toCleanup = findMatchingStrava(oldStagedActivities, oldStravaActivities).flatMap { case (k,v) => v.map(k -> _)}

    timing.logTime("SelectActivity: recentActivities")

    if (true) {
      QueueFactory.getDefaultQueue add TaskOptions.Builder.withPayload(UserCleanup(auth, neverBefore))
    }
    //println(s"Staged ${stagedActivities.mkString("\n  ")}")
    //println(s"Recent ${recentActivities.mkString("\n  ")}")

    val recentActivities = (stagedActivities diff toCleanup.map(_._1)).filter(_.id.startTime >= notBefore).sortBy(_.id.startTime)

    val recentToStrava = findMatchingStrava(recentActivities, stravaActivities ++ oldStravaActivities).filter((filterListed _).tupled)

    /*
    println(s"Staged recentActivities ${recentActivities.size}")
    println(s"Staged oldStagedActivities ${oldStagedActivities.size}")
    println(s"Staged stravaActivities ${stravaActivities.size}")
    println(s"Staged oldStravaActivities ${oldStravaActivities.size}")
    println(s"Staged toCleanup ${toCleanup.size}")
    println(s"oldStravaActivities ${oldStravaActivities.mkString("\n  ")}")
    println(s"oldStagedActivities ${oldStagedActivities.map(_.id).mkString("\n  ")}")
    */

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

        function uncheckAll() {
          $(".checkSelect").prop("checked", false);
          selectChecked();
        }
        function selectChecked() {
          // count how many are checked
          // if none or very few, hide the uncheck button
          var checked = $(".checkSelect:checked").length;
          if (checked > 2) $("#uncheckAll_button").show();
          else $("#uncheckAll_button").hide();
          if (checked > 0 ) {
            $("#div_process").show();
            $("#div_no_process").hide();
          } else {
            $("#div_process").hide();
            $("#div_no_process").show();
          }
          $("#merge_button").html(checked > 1 ? "Merge and edit ..." : "Edit ...");
        }
        """
    )}
    </script> ++
      sources(notBefore) ++
        recentToStrava.headOption.toSeq.flatMap { _ =>
          <h2>Activities</h2>
          <form id="process-form" action="process" method="post" enctype="multipart/form-data">
            <table class="activities">
              <tr>
                <th></th>
                <th align="left">Time</th>
                <th align="left">Type</th>
                <th align="left">Distance</th>
                <th align="left">Duration</th>
                <th align="left">Corresponding Strava activity</th>
                <th align="left">Data</th>
                <th align="left">Source</th>
              </tr>
              {// find most recent Strava activity
              val mostRecentStrava = stravaActivities.headOption.map(_.startTime)

              for ((actEvents, actStrava) <- recentToStrava) yield {
                //println(s"  act $actEvents $actStrava")
                val act = actEvents.id
                val ignored = actStrava.isDefined || mostRecentStrava.exists(_ >= act.startTime)
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
                  <td>{actStrava.map(hrefLink).getOrElse(NodeSeq.Empty)}</td>
                  <td>{actEvents.describeData}</td>
                  <td>{hrefLink(act)}</td>
                </tr>
              }}
            </table>

          </form>
          <div id="div_process">
            <button class="onCheckedAction" id="upload_button" onclick="submitProcess()">Send to Strava</button>
            <button class="onCheckedAction" onclick="submitDelete()">Delete from {appName}</button>
            <button id ="merge_button" class="onCheckedAction" onclick="submitEdit()">Merge and edit...</button>
            <button id ="uncheckAll_button" onclick="uncheckAll()">Uncheck all</button>
            {uploadResultsHtml()}
          </div>
          <div id="div_no_process">
            <h3>
              Select at least one activity to process it
            </h3>
          </div>
        } ++
        <script>{xml.Unparsed(
          //language=JavaScript
          """
      $("#process-form").submit(function(event) {
        // Stop the browser from submitting the form.
        event.preventDefault();
      });
      selectChecked();
      """)}
      </script>
  }

}

abstract class SelectActivity(name: String) extends DefineRequest(name) with HtmlByParts with SelectActivityPart with Headers
