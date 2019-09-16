package com.github.opengrabeso.mixtio
package requests

trait SettingsPage extends HtmlPart with ChangeSettings {
  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    val settings = Settings(auth.userId)

    val filterNames = DataStreamGPS.FilterSettings.names

    super.bodyPart(req, auth) ++
    suuntoSettings(auth.userId, settings) ++
    <hr/>
    <p>
      Elevation filter
      <select id="elev_filter" name="elev_filter" onChange={s"settingsChanged(this.id, this.value, '${auth.userId}')"}>
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
      <script src="js/script"></script>
      <script src="js/dependencies"></script>
  }

}

object SettingsPage extends DefineRequest("/settings") with HtmlByParts with SettingsPage with Headers