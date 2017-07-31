package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}
import DateTimeOps._
import org.joda.time.{DateTime => ZonedDateTime, Seconds}
import net.suunto3rdparty.Settings

object ManageActivities extends SelectActivity("/selectActivity") {
  override def title = "select activities to process"

  override def sources(before: ZonedDateTime) = {
    <div>
      <h2>Data sources</h2>
      {
        /* getSuunto is peforming cross site requests to the local server, this cannot be done on a secure page */
        val getSuuntoLink = s"window.location.assign(unsafe('getSuunto${s"?since=$before"}'))"
        <a href="javascript:;" onClick={getSuuntoLink}>Get from Suunto devices ...</a>
      }
      <a href="getFiles">Upload files...</a>
      <a href="staging">Staging...</a>
      <hr/>
    </div>
  }

  override def filterListed(activity: Main.ActivityEvents, strava: Option[Main.ActivityId]) = {
    strava.isEmpty
  }

}
