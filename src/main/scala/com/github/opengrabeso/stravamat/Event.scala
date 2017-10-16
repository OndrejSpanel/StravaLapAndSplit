package com.github.opengrabeso.stravamat
import com.github.opengrabeso.stravamat.requests.ActivityRequest
import org.joda.time.{DateTime => ZonedDateTime}

case class EventKind(id: String, display: String)

object Event {
  object Sport extends Enumeration {
    // https://strava.github.io/api/v3/uploads/
    //   ride, run, swim, workout, hike, walk, nordicski, alpineski, backcountryski, iceskate, inlineskate, kitesurf,
    //   rollerski, windsurf, workout, snowboard, snowshoe, ebikeride, virtualride

    // order by priority, roughly fastest to slowest (prefer faster sport does less harm on segments)
    // Workout (as Unknown) is the last option
    val Ride, Run, Hike, Walk, Swim, NordicSki, AlpineSki, IceSkate, InlineSkate, KiteSurf,
    RollerSki, WindSurf, Canoeing, Kayaking, Rowing, Surfing, Snowboard, Snowshoe, EbikeRide, VirtualRide, Workout = Value

    // https://support.strava.com/hc/en-us/articles/216919407-Other-Activity-Types-on-Strava

  }
  type Sport = Sport.Value

  // lower priority means more preferred
  def sportPriority(sport: Sport): Int = sport.id
}

object EventPriority {
  val seq: IndexedSeq[Class[_]] = IndexedSeq(
    classOf[BegEvent], classOf[EndEvent],
    classOf[SplitEvent],
    classOf[PauseEvent], classOf[PauseEndEvent],
    classOf[LapEvent]
  )

  def apply(e: Event) = {
    val find = seq.indexOf(e.getClass)
    //println(s"Find order of ${e.getClass} as $find")
    if (find<0) seq.size // not listed means lowest possible priority
    else find
  }
}


@SerialVersionUID(10)
sealed abstract class Event {

  import Event._

  def stamp: ZonedDateTime
  def description: String
  def isSplit: Boolean // splits need to be known when exporting
  def sportChange: Option[Event.Sport] = None
  lazy val order: Int = EventPriority(this)// higher order means less important
  def timeOffset(offset: Int): Event

  def defaultEvent: String

  protected def listSplitTypes: Seq[EventKind] = {
    Sport.values.map { s =>
      val sport = s.toString
      EventKind(s"split$sport", s"- $sport")
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

@SerialVersionUID(10)
case class PauseEvent(duration: Int, stamp: ZonedDateTime) extends Event {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description = s"Pause ${Events.niceDuration(duration)}"
  def defaultEvent = if (duration>=30) "lap" else ""
  def isSplit = false
  def priority = 20
}
@SerialVersionUID(10)
case class PauseEndEvent(duration: Int, stamp: ZonedDateTime) extends Event {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description = "Pause end"
  def defaultEvent = if (duration >= 50) "lap" else ""
  def isSplit = false
}
@SerialVersionUID(10)
case class LapEvent(stamp: ZonedDateTime) extends Event {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description = "Lap"
  def defaultEvent = "lap"
  def isSplit = false
}

@SerialVersionUID(10)
case class EndEvent(stamp: ZonedDateTime) extends Event {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description = "End"
  def defaultEvent = "end"
  def isSplit = true

  override def listTypes: Array[EventKind] = Array(EventKind("", "--"))
}

@SerialVersionUID(10)
case class BegEvent(stamp: ZonedDateTime, sport: Event.Sport) extends Event {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description = "<b>Start</b>"
  def defaultEvent = s"split${sport.toString}"
  def isSplit = true
  override def sportChange: Option[Event.Sport] = Some(sport)

  override def listTypes = listSplitTypes.toArray
}

@SerialVersionUID(10)
case class SplitEvent(stamp: ZonedDateTime, sport: Event.Sport) extends Event {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description = "Split"
  def defaultEvent = s"split${sport.toString}"
  def isSplit = true
  override def sportChange: Option[Event.Sport] = Some(sport)
}

trait SegmentTitle {
  def isPrivate: Boolean
  def name: String
  def title = {
    val segPrefix = if (isPrivate) "private " else ""
    segPrefix + Main.shortNameString("segment " + name)
  }

}

@SerialVersionUID(10)
case class StartSegEvent(name: String, isPrivate: Boolean, stamp: ZonedDateTime) extends Event with SegmentTitle {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
  def description: String = s"Start $title"
  def defaultEvent = ""
  def isSplit = false
}
@SerialVersionUID(10)
case class EndSegEvent(name: String, isPrivate: Boolean, stamp: ZonedDateTime) extends Event with SegmentTitle {
  def timeOffset(offset: Int) = copy(stamp = stamp.plusSeconds(offset)) // Find some way to DRY
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
