package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}

object SettingsPage extends DefineRequest("/settings") with ChangeSettings {
  //override def urlPrefix = "push-"
  def html(req: Request, resp: Response) = {
    val session = req.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val settings = Settings(auth.userId)
    // display push progress, once done, let user to process it
    <html>
      <head>
        {headPrefix}<title>Stravamat - settings</title>
        <script src="static/ajaxUtils.js"></script>
        <script src="static/jquery-3.2.1.min.js"></script>
      </head>
      <body>
        {bodyHeader(auth)}<h2>Settings</h2>
        {suuntoSettings(settings)}
        <script>
          updateClock();
        </script>
      </body>
    </html>

  }

}