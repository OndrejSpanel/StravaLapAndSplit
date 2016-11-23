package com.github.opengrabeso.stravalas
import com.github.opengrabeso.stravalas.requests.ActivityRequest
import org.joda.time.{DateTime => ZonedDateTime}

case class EventKind(id: String, display: String)

object Event {
  object Sport extends Enumeration {
    // https://strava.github.io/api/v3/uploads/
    // ride, run, swim, workout, hike, walk, nordicski, alpineski, backcountryski, iceskate, inlineskate, kitesurf,
    // rollerski, windsurf, workout, snowboard, snowshoe, ebikeride, virtualride
    val Ride, Run, Swim, Hike, NordicSki, Workout, Walk, AlpineSki, IceSkate, InlineSkate, KiteSurf,
    RollerSki, WindSurf, Snowboard, Snowshoe, EbikeRide, VirtualRide = Value
  }
  type Sport = Sport.Value
}

sealed abstract class Event {
  import Event._

  def stamp: ZonedDateTime
  def description: String
  def isSplit: Boolean // splits need to be known when exporting

  def defaultEvent: String

  protected def listSplitTypes: Seq[EventKind] = {
    Sport.values.map { s =>
      val sport = s.toString
      EventKind(s"split$sport", s"Activity: $sport")
    }(collection.breakOut)
  }

  def listTypes: Array[EventKind] = (Seq(
    EventKind("", "--"),
    EventKind("lap", "Lap")
  ) ++ listSplitTypes).toArray
}

object Events {

  def typeToDisplay(listTypes: Array[EventKind], name: String): String = {
    listTypes.find(_.id == name).map(_.display).getOrElse("")
  }

  def niceDuration(duration: Int): String = {
    def round(x: Int, div: Int) = (x + div / 2) / div * div
    val minute = 60
    if (duration < minute) {
      s"${round(duration, 5)} sec"
    } else {
      val minutes = duration / minute
      val seconds = duration - minutes * minute
      if (duration < 5 * minute) {
        f"$minutes:${round(seconds, 10)}%2d min"
      } else {
        s"$minutes min"
      }
    }
  }

}

case class PauseEvent(duration: Int, stamp: ZonedDateTime) extends Event {
  def description = s"Pause ${Events.niceDuration(duration)}"
  def defaultEvent = if (duration>=30) "lap" else ""
  def isSplit = false
}
case class PauseEndEvent(duration: Int, stamp: ZonedDateTime) extends Event {
  def description = "Pause end"
  def defaultEvent = if (duration >= 50) "lap" else ""
  def isSplit = false
}
case class LapEvent(stamp: ZonedDateTime) extends Event {
  def description = "Lap"
  def defaultEvent = "lap"
  def isSplit = false
}

case class EndEvent(stamp: ZonedDateTime) extends Event {
  def description = "End"
  def defaultEvent = "end"
  def isSplit = true

  override def listTypes: Array[EventKind] = Array(EventKind("", "--"))
}

case class BegEvent(stamp: ZonedDateTime, sport: Event.Sport) extends Event {
  def description = "<b>*** Start activity</b>"
  def defaultEvent = s"split${sport.toString}"
  def isSplit = true

  override def listTypes = listSplitTypes.toArray
}

case class SplitEvent(stamp: ZonedDateTime, sport: Event.Sport) extends Event {
  def description = "Split"
  def defaultEvent = s"split${sport.toString}"
  def isSplit = true
}

trait SegmentTitle {
  def isPrivate: Boolean
  def name: String
  def title = {
    val segTitle = if (isPrivate) "private segment" else "segment"
    s"$segTitle $name"
  }

}

case class StartSegEvent(name: String, isPrivate: Boolean, stamp: ZonedDateTime) extends Event with SegmentTitle {
  def description: String = s"Start $title"
  def defaultEvent = ""
  def isSplit = false
}
case class EndSegEvent(name: String, isPrivate: Boolean, stamp: ZonedDateTime) extends Event with SegmentTitle {
  def description: String = s"End $title"
  def defaultEvent = ""
  def isSplit = false
}


case class EditableEvent(var action: String, time: Int, km: Double, kinds: Array[EventKind]) {
  override def toString: String = {
    val select = ActivityRequest.htmlSelectEvent(time.toString, kinds, action)
    val selectHtmlSingleLine = select.toString.lines.mkString(" ")

    val description = s"""${Main.displaySeconds(time)} ${Main.displayDistance(km)} km $selectHtmlSingleLine"""
    s""""$action", $time, $km, '$description'"""
  }
}
