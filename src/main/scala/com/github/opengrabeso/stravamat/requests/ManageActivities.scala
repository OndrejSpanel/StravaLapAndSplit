package com.github.opengrabeso.stravamat
package requests

import org.joda.time.{DateTime => ZonedDateTime}
import Main._
import shared.Util._

object ManageActivities extends SelectActivity("/selectActivity") {
  override def title = "select activities to process"

  override def sources(before: ZonedDateTime) = {
    <div>
      <h2>Data sources</h2>
      <a href="getFiles">Upload files...</a>
      <a href="staging">Staging...</a>
      <a href="settings">Settings...</a>
      <hr/>
    </div>
  }

  override def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = {
    strava.isEmpty
  }

  override def ignoreBefore(stravaActivities: Seq[ActivityId]) = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeLast = stravaActivities.lastOption.map(_.startTime)
    val ignoreBeforeFirst = stravaActivities.headOption.map(_.startTime minusDays  14)
    val ignoreBeforeNow = new ZonedDateTime() minusMonths 2

    (Seq(ignoreBeforeNow) ++ ignoreBeforeLast ++ ignoreBeforeFirst).max
  }
}
