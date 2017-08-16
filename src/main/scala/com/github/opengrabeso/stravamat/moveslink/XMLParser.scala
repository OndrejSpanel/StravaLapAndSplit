package com.github.opengrabeso.stravamat
package moveslink

import java.io.{InputStream, PushbackInputStream}

import org.joda.time.{DateTime => ZonedDateTime, _}
import org.joda.time.format.DateTimeFormat
import java.util.regex.Pattern

import shared.Util._

import scala.collection.immutable.SortedMap
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object XMLParser {
  private val dateFormatBase = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  private def dateFormatWithZone(timezone: String) = dateFormatBase.withZone(DateTimeZone.forID(timezone))


  def parseTime(timeText: String, timezone: String): ZonedDateTime = {
    timeToUTC(ZonedDateTime.parse(timeText, dateFormatWithZone(timezone)))
  }

  def parseDuration(timeStr: String): Duration = {
    val relTime = LocalTime.parse(timeStr)
    val ms = relTime.getMillisOfDay
    new Duration(ms)
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

  def parseXML(fileName: String, document: InputStream, maxHR: Int, timezone: String): Seq[Move] = {

    import SAXParser._
    object parsed extends SAXParserWithGrammar {
      var deviceName = Option.empty[String]

      class Move {
        //var calories = Option.empty[Int]
        //var distance = Option.empty[Int]
        var startTime = Option.empty[ZonedDateTime]
        var durationMs: Int = 0
        var activityType: MoveHeader.ActivityType = MoveHeader.ActivityType.Unknown
        var lapDurations = ArrayBuffer.empty[Duration]
        var distanceSamples = Seq.empty[Double]
        var heartRateSamples = Seq.empty[Int]
      }

      val moves = ArrayBuffer.empty[Move]

      def grammar = new XMLTag("<root>",
        new XMLTag("Device",
          new ProcessText("FullName", text => parsed.deviceName = Some(text))
        ),
        new XMLTag("Move",
          new XMLTag("Header",
            new ProcessText("Duration", {text =>
              val durationPattern = Pattern.compile("(\\d+):(\\d+):(\\d+)\\.?(\\d*)")
              val matcher = durationPattern.matcher(text)
              val duration = if (matcher.matches) {
                val hour = matcher.group(1).toInt
                val minute = matcher.group(2).toInt
                val second = matcher.group(3).toInt
                val ms = if (!matcher.group(4).isEmpty) matcher.group(4).toInt else 0
                (hour * 3600 + minute * 60 + second) * 1000 + ms
              } else 0
              moves.last.durationMs = duration
            }),
            new ProcessText("Time", { text =>
              val startTime = parseTime(text, timezone)
              moves.last.startTime = Some(startTime)
            }),
            new ProcessText("Activity", {text =>
              import MoveHeader.ActivityType._
              val sportType = Try(text.toInt).getOrElse(0)
              // TODO: add at least most common sports
              val activityType = sportType match {
                case 82 => RunningTrail
                case 75 => Orienteering
                case 5 => MountainBike
                case _ => Unknown
              }
              moves.last.activityType = activityType
            })
            /* never used, no need to parse
            case _ / "Move" / "Header" / "Calories" =>
              moves.last.calories = Some(text.toInt)
            case _ / "Move" / "Header" / "Distance" =>
              moves.last.distance = Some(text.toInt)
            */

          ),
          new XMLTag("Samples",
            new ProcessText("Distance", {text =>
              moves.last.distanceSamples = text.split(" ").dropWhile(_ == "").scanLeft(0.0)(_ + _.toDouble)
            }),
            new ProcessText("HR", {text =>
              def duplicateHead(strs: Seq[String]) = {
                if (strs.head.isEmpty) strs.tail.head +: strs.tail
                else strs
              }

              moves.last.heartRateSamples = duplicateHead(text.split(" ")).map(_.toInt)
            })
            // TODO: Cadence, Power, Temperature ...
          ),
          new XMLTag("Marks",
            new XMLTag("Mark",
              new ProcessText("Time", text => moves.last.lapDurations appendAll Try(parseDuration(text)).toOption)
            )
          )
        ) {
          override def open() = moves += new Move
        }

      )
    }

    parse(document)(parsed)

    for (i <- parsed.moves.indices) yield {
      val mi = parsed.moves(i)
      val validatedHR = mi.heartRateSamples.map {
        hr =>
          if (hr > maxHR) None
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

      val timeRange = 0 until mi.durationMs by 10000

      def timeMs(ms: Int) = mi.startTime.get.plusMillis(ms)

      val timedMapHR = (timeRange zip validatedCleanedHR).collect { case (t, Some(s)) =>
        timeMs(t) -> s
      }

      val timedMapDist = (timeRange zip mi.distanceSamples).collect { case (t, d) =>
        timeMs(t) -> d
      }

      val header = new MoveHeader(parsed.deviceName.toSet, mi.activityType)
      val hrStream = new DataStreamHR(SortedMap(timedMapHR: _*))
      val distStream = new DataStreamDist(SortedMap(timedMapDist: _*))

      val laps = mi.lapDurations.scanLeft(mi.startTime.get) { (time, duration) => time.plus(duration)}

      val move = new Move(Set(fileName), header, hrStream, distStream)
      if (laps.nonEmpty) {
        move.addStream(move, new DataStreamLap(SortedMap(laps.map(time => time -> "Manual"): _*)))
      } else {
        move
      }
    }

  }

}
