package com.github.opengrabeso.stravamat
package requests

import Main._
import org.joda.time.{DateTime => ZonedDateTime}

object ManageActivities extends SelectActivity("/selectActivity") {
  override def title = "select activities to process"

  override def sources(before: ZonedDateTime) = {
    <div>
      <h2>Data sources</h2>
      <a href="getFiles">Upload files...</a>
      <a href="staging">Staging...</a>
      <hr/>
    </div>
  }

  override def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = {
    strava.isEmpty
  }

}
