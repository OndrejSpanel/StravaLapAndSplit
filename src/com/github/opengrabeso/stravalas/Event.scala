package com.github.opengrabeso.stravalas

case class Stamp(time: Int, dist: Double)

case class EventKind(id: String, display: String)

sealed abstract class Event {
  def stamp: Stamp
  def description: String

  def id: String = stamp.time.toString
  def defaultEvent: String
}

object Events {
  def listTypes: Array[EventKind] = Array(
    EventKind("lap", "Lap"),
    EventKind("split", "Split"),
    EventKind("", "--")
  )

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
        s"$minutes"
      }
    }
  }

}

case class PauseEvent(duration: Int, stamp: Stamp) extends Event {
  def description = s"Pause ${Events.niceDuration(duration)}"
  def defaultEvent = if (duration >= 30) "split" else if (duration>=15) "lap" else ""
}
case class LapEvent(stamp: Stamp) extends Event {
  def description = "Lap"
  def defaultEvent = "lap"
}

trait SegmentTitle {
  def isPrivate: Boolean
  def name: String
  def title = {
    val segTitle = if (isPrivate) "private segment" else "segment"
    s"$segTitle $name"
  }

}

case class StartSegEvent(name: String, isPrivate: Boolean, stamp: Stamp) extends Event with SegmentTitle {
  def description: String = s"Start $title"
  def defaultEvent = ""
}
case class EndSegEvent(name: String, isPrivate: Boolean, stamp: Stamp) extends Event with SegmentTitle {
  def description: String = s"End $title"
  def defaultEvent = ""
}
