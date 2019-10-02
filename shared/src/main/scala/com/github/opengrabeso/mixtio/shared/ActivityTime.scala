package com.github.opengrabeso.mixtio
package shared
import java.time.ZonedDateTime
import common.Util._

object ActivityTime {
  def alwaysIgnoreBefore(stravaActivities: Seq[ZonedDateTime]): ZonedDateTime = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeNow = ZonedDateTime.now() minusYears  2
    (Seq(ignoreBeforeNow) ++ stravaActivities.lastOption).max
  }

  def defaultIgnoreBefore(stravaActivities: Seq[ZonedDateTime]): ZonedDateTime = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeLast = alwaysIgnoreBefore(stravaActivities)
    // ignore anything older than 14 days before most recent Strava activity
    val ignoreBeforeFirst = stravaActivities.headOption.map(_ minusDays  14)
    // ignore anything older than 2 months from now
    val ignoreBeforeNow = ZonedDateTime.now() minusMonths 2

    (Seq(ignoreBeforeNow, ignoreBeforeLast) ++ ignoreBeforeFirst).max
  }

}
