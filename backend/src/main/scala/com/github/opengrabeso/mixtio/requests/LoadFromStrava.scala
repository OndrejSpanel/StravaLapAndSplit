package com.github.opengrabeso.mixtio
package requests

import Main._

trait LoadFromStrava extends HtmlPart with ActivitiesTable {
  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    headPrefix ++ super.headerPart(req, auth) ++
    <title>{appName} - get activities from Strava</title>
    <script src="static/timeUtils.js"></script>
  }

  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    val activities = Main.stravaActivitiesNotStaged(auth)
    super.bodyPart(req, auth) ++
    <table class="activities">
      <tr>
        <th align="left">Time</th>
        <th align="left">Type</th>
        <th align="left">Distance</th>
        <th align="left">Duration</th>
        <th align="left">View on Strava</th>
        <th align="left">Strava Id</th>
        <th align="left">Click to edit</th>
      </tr>
      {for (act <- activities) yield {
        <tr>
          <td>{jsResult(jsDateRange(act.startTime, act.endTime))}</td>
          <td>{act.sportName}</td>
          <td>{Main.displayDistance(act.distance)}</td>
          <td>{Main.displaySeconds(act.duration)}</td>
          <td>{hrefLink(act)}</td>
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