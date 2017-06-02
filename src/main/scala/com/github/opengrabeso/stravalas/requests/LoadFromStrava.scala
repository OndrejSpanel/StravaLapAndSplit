package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object LoadFromStrava extends DefineRequest("/loadFromStrava") {
  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val activities = Main.stravaActivitiesNotStaged(auth)
    <html>
      <head>
        {headPrefix}<title>Stravamat - select activity</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
      </head>
      <body>
        {bodyHeader(auth)}<table class="activities">
        {for (act <- activities) yield {
          <tr>
            <td>{act.id.stravaId}</td>
            <td>{act.sportName}</td>
            <td>{act.hrefLink}</td>
            <td>{Main.displayDistance(act.distance)}km</td>
            <td>{Main.displaySeconds(act.duration)}</td>
            <td>
              <form action="activityFromStrava" method="get">
                <input type="hidden" name="activityId" value={act.id.stravaId}/>
                <input type="submit" value=">>"/>
              </form>
            </td>
          </tr>
        }}
      </table>{cond(activities.nonEmpty) {
        <form action="activityFromStrava" method="get">
          <p>Other activity Id:
            <input type="text" name="activityId" value={activities(0).id.stravaId}/>
            <input type="submit" value="Submit"/>
          </p>
        </form>
      }}{bodyFooter}
      </body>
    </html>
  }
}
