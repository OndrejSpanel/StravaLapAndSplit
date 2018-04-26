package com.github.opengrabeso.stravamat
package requests

trait SettingsPage extends HtmlPart with ChangeSettings {
  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    val settings = Settings(auth.userId)

    val filterNames = DataStreamGPS.FilterSettings.names

    super.bodyPart(req, auth) ++
    suuntoSettings(settings) ++
    <hr/>
    <p>
      Elevation filter
      <select id="elev_filter" name="elev_filter" onChange="settingsChangedOption(this)">
        {for ((name, i) <- filterNames) yield {
        <option value={i.toString} selected={if (i == settings.elevFilter) "" else null}>
          {name}
        </option>
      }}
      </select>
    </p>
  }

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    <title>{shared.appName} - settings</title>
    <script src="static/ajaxUtils.js"></script>
    <script src="static/jquery-3.2.1.min.js"></script>
      <script>{xml.Unparsed(
        //language=JavaScript
        """
        function settingsChangedOption(target) {
          var name = target.id;
          // send the new settings to the server
          var v = target.value;
          ajaxAsync("save-settings?" + name + "=" + v, function(response) {});
        }
        """)}


    </script>
  }

}

object SettingsPage extends DefineRequest("/settings") with HtmlByParts with SettingsPage with Headers