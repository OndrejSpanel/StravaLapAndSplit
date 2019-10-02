package com.github.opengrabeso.mixtio
package common.model

import java.time.temporal.ChronoUnit

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

import common.Util._
import SportId._

@SerialVersionUID(11L)
case class ActivityId(id: FileId, digest: String, name: String, startTime: ZonedDateTime, endTime: ZonedDateTime, sportName: SportId, distance: Double) {

  override def toString = s"${id.toString} - $name ($startTime..$endTime)"

  def secondsInActivity(time: ZonedDateTime): Int = ChronoUnit.SECONDS.between(startTime, time).toInt

  val duration: Int = ChronoUnit.SECONDS.between(startTime, endTime).toInt

  def timeOffset(offset: Int): ActivityId = copy(startTime = startTime plusSeconds offset, endTime = endTime plusSeconds offset)

  def isMatching(that: ActivityId): Boolean = {
    // check overlap time

    val commonBeg = Seq(startTime,that.startTime).max
    val commonEnd = Seq(endTime,that.endTime).min
    if (commonEnd > commonBeg) {
      val commonDuration = ChronoUnit.SECONDS.between(commonBeg, commonEnd)
      commonDuration > (duration min that.duration) * 0.75f
    } else false
  }

  def link: String = {
    id match {
      case FileId.StravaId(num) =>
        s"https://www.strava.com/activities/$num"
      case _ =>
        null // not a Strava activity - no link
    }
  }


  def shortName: String = {
    common.Formatting.shortNameString(name)
  }
}

object ActivityId extends EnhancedRestDataCompanion[ActivityId]