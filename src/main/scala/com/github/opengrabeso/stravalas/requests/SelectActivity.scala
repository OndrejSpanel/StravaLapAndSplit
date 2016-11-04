package com.github.opengrabeso.stravalas
package requests

import javax.servlet.http.HttpServletResponse

import spark.{Request, Response}

import scala.util.Try

object SelectActivity extends DefineRequest {
  def handle = Handle("selectActivity")

  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val code = request.queryParams("code")
    val authResult = Try {
      Main.stravaAuth(code)
    }
    authResult.map { auth =>
      resp.cookie("authCode", code, 3600 * 24 * 30) // 30 days
      session.attribute("auth", auth)
      val activities = Main.lastActivities(auth.token)
      <html>
        <head>
          {headPrefix}<title>Strava Split And Lap - select activity</title>
          <style>
            tr.activities:nth-child(even) {{background-color: #f2f2f2}}
            tr.activities:hover {{background-color: #f0f0e0}}
          </style>
        </head>
        <body>
          {bodyHeader(auth)}

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
          {cond(activities.length > 0) {
          <form action="activity" method="get">
            <p>Other activity Id:
              <input type="text" name="activityId" value={activities(0).id.toString}/>
              <input type="submit" value="Submit"/>
            </p>
          </form>
        }
        <form action="upload" method="post" enctype="multipart/form-data">
          <p>Select files to upload
            <input type="file" name="activities" multiple="multiple" accept=".fit,.gpx,.tcx,.sml,.xml"/>
          </p>
          <input type="submit" value="Upload"/>
        </form>}
          {bodyFooter}
        </body>
      </html>
    }
  }.getOrElse {
    resp.cookie("authCode", "", 0) // delete the cookie
    resp.redirect("/", HttpServletResponse.SC_MOVED_TEMPORARILY)
    Nil
  }
}
