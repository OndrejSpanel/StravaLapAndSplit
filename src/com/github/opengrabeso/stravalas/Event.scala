package com.github.opengrabeso.stravalas

case class Stamp(time: Int, dist: Double) {
  def offset(t: Int, d: Double) = Stamp(time + t, dist + d)
}

case class EventKind(id: String, display: String)

sealed abstract class Event {
  def stamp: Stamp
  def description: String
  def isSplit: Boolean

  def id: String = stamp.time.toString
  def defaultEvent: String

  def link(id: Long, authToken: String): String = ""

  protected def listSplitTypes: Seq[EventKind] = Seq(
    EventKind("split", "Split"),
    EventKind("splitSwim", "Split (Swim)"),
    EventKind("splitRun", "Split (Run)"),
    EventKind("splitRide", "Split (Ride)")
  )

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

trait SplitLink extends Event {
  override def link(id: Long, authToken: String): String =
    s"""
       |<form action="download" method="post">
       |  <input type="hidden" name="id" value="$id"/>
       |  <input type="hidden" name="auth_token" value="$authToken"/>
       |  <input type="hidden" name="operation" value="split"/>
       |  <input type="hidden" name="time" value="${stamp.time}"/>
       |  <input type="submit" value="Save activity"/>
       |</form>""".stripMargin
}

case class PauseEvent(duration: Int, stamp: Stamp) extends Event {
  def description = s"Pause ${Events.niceDuration(duration)}"
  def defaultEvent = if (duration >= 40) "split" else if (duration>=15) "lap" else ""
  def isSplit = false
}
case class PauseEndEvent(duration: Int, stamp: Stamp) extends Event {
  def description = s"Pause end"
  def defaultEvent = if (duration >= 30) "lap" else ""
  def isSplit = false
}
case class LapEvent(stamp: Stamp) extends Event {
  def description = "Lap"
  def defaultEvent = "lap"
  def isSplit = false
}

case class EndEvent(stamp: Stamp) extends Event {
  def description = "End"
  def defaultEvent = "end"
  def isSplit = true

  override def listTypes: Array[EventKind] = Array(EventKind("", "--"))
}

case class BegEvent(stamp: Stamp) extends Event with SplitLink {
  def description = "<b>*** Start activity</b>"
  def defaultEvent = "split"
  def isSplit = true

  override def listTypes = listSplitTypes.toArray
}

case class SplitEvent(stamp: Stamp) extends Event with SplitLink {
  def description = "Split"
  def defaultEvent = "split"
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

case class StartSegEvent(name: String, isPrivate: Boolean, stamp: Stamp) extends Event with SegmentTitle {
  def description: String = s"Start $title"
  def defaultEvent = ""
  def isSplit = false
}
case class EndSegEvent(name: String, isPrivate: Boolean, stamp: Stamp) extends Event with SegmentTitle {
  def description: String = s"End $title"
  def defaultEvent = ""
  def isSplit = false
}
