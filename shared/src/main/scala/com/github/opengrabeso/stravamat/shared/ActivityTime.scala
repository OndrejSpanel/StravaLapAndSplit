package com.github.opengrabeso.stravamat
package shared
import org.joda.time.{DateTime => ZonedDateTime}
import Util._

object ActivityTime {
  def alwaysIgnoreBefore(stravaActivities: Seq[ZonedDateTime]): ZonedDateTime = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeNow = new ZonedDateTime() minusYears  2
    (Seq(ignoreBeforeNow) ++ stravaActivities.lastOption).max
  }

  def defaultIgnoreBefore(stravaActivities: Seq[ZonedDateTime]): ZonedDateTime = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeLast = alwaysIgnoreBefore(stravaActivities)
    // ignore anything older than 14 days before most recent Strava activity
    val ignoreBeforeFirst = stravaActivities.headOption.map(_ minusDays  14)
    // ignore anything older than 2 months from now
    val ignoreBeforeNow = new ZonedDateTime() minusMonths 2

    (Seq(ignoreBeforeNow, ignoreBeforeLast) ++ ignoreBeforeFirst).max
  }

}
