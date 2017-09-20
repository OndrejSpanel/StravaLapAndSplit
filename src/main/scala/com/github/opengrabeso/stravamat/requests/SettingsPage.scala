package com.github.opengrabeso.stravamat
package requests

import Main._
import spark.Request

trait AddSettings extends HtmlPart with ChangeSettings {
  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    val settings = Settings(auth.userId)

    super.bodyPart(req, auth) ++
    suuntoSettings(settings) ++
    <script>
      updateClock();
    </script>
  }

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    <title>Stravamat - settings</title>
    <script src="static/ajaxUtils.js"></script>
    <script src="static/jquery-3.2.1.min.js"></script>
  }

}

object SettingsPage extends DefineRequest("/settings") with HtmlByParts with AddSettings with Headers