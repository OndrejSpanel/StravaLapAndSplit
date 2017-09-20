package com.github.opengrabeso.stravamat
package requests

import Main._
import spark.Request

object SettingsPage extends DefineRequest("/settings") with ChangeSettings with HtmlPart {

  override def bodyPart(req: Request, auth: StravaAuthResult) = {
    val settings = Settings(auth.userId)

    super.bodyPart(req, auth) ++
    bodyHeader(auth) ++ // TODO: convert header to HtmlPart
    <h2>Settings</h2> ++
    suuntoSettings(settings) ++
    <script>
      updateClock();
    </script>
  }

  override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    headPrefix ++
    <title>Stravamat - settings</title>
    <script src="static/ajaxUtils.js"></script>
    <script src="static/jquery-3.2.1.min.js"></script>
  }


}