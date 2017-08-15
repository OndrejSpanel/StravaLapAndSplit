package com.github.opengrabeso.stravamat
package moveslink

import java.io.{InputStream, PushbackInputStream}

import org.joda.time.{DateTime => ZonedDateTime, _}
import org.joda.time.format.{DateTimeFormat, PeriodFormat, PeriodFormatter}
import java.util.regex.Pattern

import scala.xml._
import java.util.logging.Logger
import javax.swing.text.html.HTMLFrameHyperlinkEvent

import shared.Util._

import scala.collection.immutable.SortedMap
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}
import scala.xml.pull.XMLEventReader

object XMLParser {
  private val log = Logger.getLogger(XMLParser.getClass.getName)
  private val dateFormatBase = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  private def dateFormatWithZone(timezone: String) = dateFormatBase.withZone(DateTimeZone.forID(timezone))

  def parseSamples(fileName: String, header: Header, samples: Node, maxHR: Int): Move = {
    val distanceStr = (samples \ "Distance")(0).text
    val heartRateStr = (samples \ "HR")(0).text
    def insertZeroHead(strs: Seq[String]) = {
      if (strs.head.isEmpty) "0" +: strs.tail
      else strs
    }
    def duplicateHead(strs: Seq[String]) = {
      if (strs.head.isEmpty) strs.tail.head +: strs.tail
      else strs
    }

    var currentSum: Double = 0
    val distanceSamples = for {
      distance <- insertZeroHead(distanceStr.split(" "))
    } yield {
      currentSum += distance.toDouble
      currentSum
    }
    val heartRateSamples = for {
      heartRate <- duplicateHead(heartRateStr.split(" "))
    } yield {
      heartRate.toInt
    }

    val validatedHR = heartRateSamples.map {
      hr =>
        if (hr > maxHR) None // TODO: remove neighbouring samples as well
        else Some(hr)
    }

    // drop two samples around each None

    def slidingRepeatHeadTail[T](s: Seq[T], slide: Int) = {
      val prefix = Seq.fill(slide / 2)(s.head)
      val postfix = Seq.fill(slide - 1 - slide / 2)(s.last)
      val slideSource = prefix ++ s ++ postfix
      slideSource.sliding(slide)
    }

    val slide5 = slidingRepeatHeadTail(validatedHR, 5)

    val validatedCleanedHR = slide5.map {
      case s5 if !s5.contains(None) => s5(2)
      case _ => None
    }.toIndexedSeq

    val timeRange = 0 until header.durationMs by 10000

    def timeMs(ms: Int) = header.startTime.plusMillis(ms)

    val timedMapHR = (timeRange zip validatedCleanedHR).collect { case (t, Some(s)) =>
      timeMs(t) -> s
    }

    val timedMapDist = (timeRange zip distanceSamples).collect { case (t, d) =>
      timeMs(t) -> d
    }

    val hrStream = new DataStreamHR(SortedMap(timedMapHR:_*))
    val distStream = new DataStreamDist(SortedMap(timedMapDist:_*))
    new Move(Set(fileName), header.moveHeader, hrStream, distStream)
  }

  def parseTime(timeText: String, timezone: String): ZonedDateTime = {
    timeToUTC(ZonedDateTime.parse(timeText, dateFormatWithZone(timezone)))
  }


  def parseHeader(headerStr: Node, deviceName: Option[String], timezone: String) = {

    val durationPattern = Pattern.compile("(\\d+):(\\d+):(\\d+)\\.?(\\d*)")

    val calories = (headerStr \ "Calories")(0).text.toInt
    val distance = (headerStr \ "Distance")(0).text.toInt

    val sportType = Try((headerStr \ "Activity")(0).text.toInt).getOrElse(0)

    import MoveHeader.ActivityType._
    // TODO: add at least most common sports
    val activityType = sportType match {
      case 82 => RunningTrail
      case 75 => Orienteering
      case 5 => MountainBike
      case _ => Unknown
    }

    val timeText = (headerStr \ "Time") (0).text
    val startTime = parseTime(timeText, timezone)
    val durationStr = (headerStr \ "Duration")(0).text
    val matcher = durationPattern.matcher(durationStr)
    val duration = if (matcher.matches) {
      val hour = matcher.group(1).toInt
      val minute = matcher.group(2).toInt
      val second = matcher.group(3).toInt
      val ms = if (!matcher.group(4).isEmpty) matcher.group(4).toInt else 0
      (hour * 3600 + minute * 60 + second) * 1000 + ms
    } else 0
    Header(MoveHeader(deviceName.toSet, activityType), startTime, duration, calories, distance)
  }

  def skipMoveslinkDoctype(is: InputStream): InputStream = {
    val pbStream =  new PushbackInputStream(is, 100)
    val wantedPrefix = """<?xml version="1.0" encoding="ISO-8859-1"?>"""
    val prefixToRemove = """<!DOCTYPE xml>"""

    @scala.annotation.tailrec
    def skipPrefix(p: List[Byte]): Boolean = {
      if (p.isEmpty) true
      else {
        val c = pbStream.read()
        if (c == p.head) skipPrefix(p.tail)
        else {
          pbStream.unread(c)
          false
        }
      }
    }
    @scala.annotation.tailrec
    def skipEmptyLines(): Unit = {
      val c = pbStream.read().toChar
      if (c.isWhitespace) skipEmptyLines()
      else pbStream.unread(c)
    }

    val wantedPresent = skipPrefix(wantedPrefix.getBytes.toList)
    skipEmptyLines()
    skipPrefix(prefixToRemove.getBytes.toList)
    skipEmptyLines()

    if (wantedPresent) {
      pbStream.unread(wantedPrefix.getBytes)
    }
    pbStream
  }

  def parseXML(fileName: String, document: XMLEventReader, maxHR: Int, timezone: String): Seq[Try[Move]] = {

    // reverse :: associativity so that paths can be written in a natural order
    object / {
      def unapply(arg: Seq[String]): Option[(Seq[String], String)] = {
        arg match {
          case head +: tail => Some(tail, head)
          case _ => None
        }
      }
    }

    object parsed extends SAXParser.Events {
      var deviceName = Option.empty[String]

      class Move {
        var calories = Option.empty[Int]
        var distance = Option.empty[Int]
        var time = Option.empty[ZonedDateTime]
        var duration = Option.empty[Int]
        var activityType = Option.empty[MoveHeader.ActivityType]
      }
      val moves = ArrayBuffer.empty[Move]

      def open(path: Seq[String]) = {
        path match {
          case _ / "Moves" / "Move" =>
            moves.append(new Move)
          case _ =>
        }
      }

      def read(path: Seq[String], text: String) = {
        path match {
          case _ / "Device" / "FullName" =>
            parsed.deviceName = Some(text)
          case _ / "Move" / "Header" / "Calories" =>
            moves.last.calories = Some(text.toInt)
          case _ / "Move" / "Header" / "Distance" =>
            moves.last.distance = Some(text.toInt)
          case _ / "Move" / "Header" / "Duration" =>
            val durationPattern = Pattern.compile("(\\d+):(\\d+):(\\d+)\\.?(\\d*)")
            val matcher = durationPattern.matcher(text)
            val duration = if (matcher.matches) {
              val hour = matcher.group(1).toInt
              val minute = matcher.group(2).toInt
              val second = matcher.group(3).toInt
              val ms = if (!matcher.group(4).isEmpty) matcher.group(4).toInt else 0
              (hour * 3600 + minute * 60 + second) * 1000 + ms
            } else 0
            moves.last.duration = Some(duration)

          case _ / "Move" / "Header" / "Time" =>
            val startTime = parseTime(text, timezone)
            moves.last.time = Some(startTime)
          case _ / "Move" / "Header" / "Activity" =>
            import MoveHeader.ActivityType._
            val sportType = Try(text.toInt).getOrElse(0)
            // TODO: add at least most common sports
            val activityType = sportType match {
              case 82 => RunningTrail
              case 75 => Orienteering
              case 5 => MountainBike
              case _ => Unknown
            }
            moves.last.activityType = Some(activityType)
          case _ / "Move" / "Samples" / "Distance" =>
          case _ / "Move" / "Samples" / "Cadence" =>
          case _ / "Move" / "Samples" / "HR" =>


          case _ =>
        }

      }

      def close(path: Seq[String]) = {

      }
    }

    SAXParser.parse(document)(parsed)

    ???
    /*
    val moves = document \ "Moves"

    val moveList = moves \ "Move"
    XMLParser.log.fine(moveList.size + " move elements in this file")
    val suuntoMoves = moveList.zipWithIndex.map { case (moveItem, i) =>
      try {
        val headerNode = (moveItem \ "Header")(0)
        val samples = (moveItem \ "Samples")(0)
        val header = parseHeader(headerNode, deviceName, timezone)

        def parseDuration(timeStr: String): Duration = {
          val relTime = LocalTime.parse(timeStr)
          val ms = relTime.getMillisOfDay
          new Duration(ms)
        }

        val lapDurations = for {
          mark <- moveItem \ "Marks" \ "Mark"
          lapDuration <- Try (parseDuration((mark \ "Time")(0).text)).toOption
        } yield {
          lapDuration
        }

        val laps = lapDurations.scanLeft(header.startTime) { (time, duration) => time.plus(duration)}

        val suuntoMove = parseSamples(fileName, header, samples, maxHR)

        val moveWithLaps = if (laps.nonEmpty) {
          suuntoMove.addStream(suuntoMove, new DataStreamLap(SortedMap(laps.map(time => time -> "Manual"): _*)))
        } else suuntoMove
        Success(moveWithLaps)
      }
      catch {
        case ex: Exception =>
          XMLParser.log.info(s"Data invalid in the no. ${i + 1} of the moves")
          //println(ex.printStackTrace)
          Failure(ex)
      }
    }
    suuntoMoves
    */
  }

}
