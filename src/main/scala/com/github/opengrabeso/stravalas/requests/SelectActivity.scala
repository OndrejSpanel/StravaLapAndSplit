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

  def htmlActivityAction(id: FileId, types: Seq[ActivityAction], action: ActivityAction) = {
    val idString = id.toString
    //<select id={idString} name={s"id=$idString"} onchange={s"changeActivity(this, this.options[this.selectedIndex].value, '$idString')"}>
    <select id={idString} name={s"id=$idString"}>
      {for (et <- types) yield {
      <option value={et.id.toString} selected={if (action == et) "" else null}>
        {displayActivityAction(et)}
      </option>
    }}
    </select>
  }

  def jsResult(func: String) = {

    val toRun = s"function () {return $func}()"

    <script>document.write({xml.Unparsed(toRun)})</script>
  }

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
          """
        )}
        </script>
        <script src="static/ajaxUtils.js"></script>
      </head>
      <body>
        {bodyHeader(auth)}

        <h2>Data sources</h2>
        <a href="loadFromStrava">Load from Strava ...</a>
        {
        /* getSuunto is peforming cross site requests to the local server, this cannot be done on a secure page */

        val sincePar = ignoreBefore.fold("")("?since=" + _.toString)
        val getSuuntoLink = s"window.location.assign(unsafe('getSuunto$sincePar'))"
        <a href="javascript:;" onClick={getSuuntoLink}>Get from Suunto devices ...</a>
        }
        <a href="getFiles">Upload files...</a>
        <hr/>
        <h2>Staging</h2>
        <form action="activity" method="post" enctype="multipart/form-data">
          <table class="activities">
            {for ((actEvents, actStrava) <- recentToStrava) yield {
              val act = actEvents.id
              // once any activity is present on Strava, do not offer upload by default any more
              // (if some earlier is not present, it was probably already uploaded and deleted)
              if (actStrava.isDefined) ignored = true
              val action = if (ignored) ActIgnore else ActUpload
              <tr>
                <td><button onclick={s"ajaxAction('delete?id=${act.id.toString}')"}>Unstage</button></td>
                <td>{jsResult(Main.jsDateRange(act.startTime, act.endTime))}</td>
                <td>{act.sportName}</td>
                <td>{if (actEvents.hasGPS) "GPS" else "--"}</td>
                <td>{act.hrefLink}</td>
                <td>{Main.displayDistance(act.distance)} km</td>
                <td>{Main.displaySeconds(act.duration)}</td>
                <td>{htmlActivityAction(act.id, actions, action)}</td>
                <td>{actStrava.fold(<div>{act.id.toString}</div>)(_.hrefLink)}</td>
              </tr>
          }}
          </table>
          <input type="submit" value="Process..."/>
        </form>
        {bodyFooter}
      </body>
    </html>
  }

}