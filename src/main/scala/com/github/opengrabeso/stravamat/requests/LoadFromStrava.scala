package com.github.opengrabeso.stravamat
package requests

import Main._

trait LoadFromStrava extends HtmlPart with ActivitiesTable {
  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    headPrefix ++ super.headerPart(req, auth) ++
    <title>Stravamat - get activities from Strava</title>
    <script src="static/timeUtils.js"></script>
  }

  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    val activities = Main.stravaActivitiesNotStaged(auth)
    super.bodyPart(req, auth) ++
    <table class="activities">
    {for (act <- activities) yield {
      <tr>
        <td>{jsResult(jsDateRange(act.startTime, act.endTime))}</td>
        <td>{act.sportName}</td>
        <td>{act.hrefLink}</td>
        <td>{Main.displayDistance(act.distance)}km</td>
        <td>{Main.displaySeconds(act.duration)}</td>
        <td>{act.id.stravaId}</td>
        <td>
          <form action="activityFromStrava" method="get">
            <input type="hidden" name="activityId" value={act.id.stravaId}/>
            <input type="submit" value=">>"/>
          </form>
        </td>
      </tr>
    }}
    </table> ++ {cond(activities.nonEmpty) {
      <form action="activityFromStrava" method="get">
        <p>Other activity Id:
          <input type="text" name="activityId" value={activities(0).id.stravaId}/>
          <input type="submit" value="Submit"/>
        </p>
      </form>
    }}
  }
}

object LoadFromStrava extends DefineRequest("/loadFromStrava") with HtmlByParts with LoadFromStrava with Headers