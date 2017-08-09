package com.github.opengrabeso.stravamat

import java.io.InputStream

import com.github.opengrabeso.stravamat.Event.Sport
import MoveHeader.ActivityType._

import scala.collection.immutable.SortedMap
import org.joda.time.{DateTime => ZonedDateTime}
import DateTimeOps._
import FileId._

import scala.xml.{Node, XML}

object MoveslinkImport {


  def sportFromActivityType(at: MoveHeader.ActivityType): Sport = {
    at match {
      case RunningTrail => Sport.Run
      case RunningRoad => Sport.Run
      case Orienteering => Sport.Run
      case MountainBike => Sport.Ride
      case Cycling => Sport.Ride
      case Unknown => Sport.Workout
    }
  }

  def loadFromMove(fileName: String, digest: String, move: Move): Option[Main.ActivityEvents] = {
    // Move(fileName: Set[String], header: MoveHeader, streams: Map[Class[_], DataStream[_]]) {
    // ActivityEvents(id: ActivityId, events: Array[Event], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream[_]]) {

    def distFromHRStream(hr: DataStreamHRWithDist): DataStreamDist = {
      val stream = hr.mapStreamValues(_.dist)
      new DataStreamDist(stream)
    }

    def hrFromHRStream(hr: DataStreamHRWithDist): DataStreamHR = {
      val stream = hr.mapStreamValues(_.hr)
      new DataStreamHR(stream)
    }

    val distStream = move.streamGet[DataStreamDist] orElse move.streamGet[DataStreamHRWithDist].map(distFromHRStream)

    val laps = move.streamGet[DataStreamLap]

    val gps = move.streamGet[DataStreamGPS].getOrElse(new DataStreamGPS(SortedMap.empty[ZonedDateTime, GPSPoint]))

    val dist = distStream.getOrElse(new DataStreamDist(SortedMap.empty[ZonedDateTime, Double]))

    // TODO: other attributes
    val hrStream = move.streamGet[DataStreamHRWithDist].map(hrFromHRStream).getOrElse(new DataStreamHR(SortedMap.empty[ZonedDateTime, Int]))

    for {
      startTime <- move.startTime
      endTime <- move.endTime
      d <- dist.stream.lastOption.map(_._2)
    } yield {
      val sport = sportFromActivityType(move.header.moveType)

      /*
      // timestamp around 2017 needs 30 bits: (2017-1970)*365*24*3600 = (2017-1970)*365*24*3600
      val startInSec = startTime.getMillis / 1000
      val durationInSec = endTime.getMillis / 1000 - startInSec // 18 b is enough for 3 days - ln(3*24*3600)/ln(2) = 17.98
      val sportId = sport.id // 8b should be enough

      val startBits = 32
      val durationBits = 18
      val sportBits = 8
      */

      val id = Main.ActivityId(FilenameId(fileName), digest, "Activity", startTime, endTime, sport, d)

      val events = Array[Event](BegEvent(id.startTime, sport), EndEvent(id.endTime))

      // TODO: avoid duplicate timestamp events
      val lapEvents = laps.toList.flatMap(_.stream.keys.map(LapEvent))

      val allEvents = (events ++ lapEvents).sortBy(_.stamp)

      Main.ActivityEvents(id, allEvents, dist, gps, Seq(hrStream))
    }
  }

  def loadSml(fileName: String, digest: String, stream: InputStream): Option[Move] = {

    def now() = System.currentTimeMillis()
    val start = now()
    def logTime(msg: String) = println(s"$msg: time ${now()-start}")

    def getDeviceLog(doc: Node): Node = (doc \ "DeviceLog") (0)

    logTime(s"start $fileName")
    val doc = XML.load(stream)
    logTime("XML.load")

    val dev = getDeviceLog(doc)
    logTime("getDeviceLog")

    val ret = moveslink2.XMLParser.parseXML(fileName, dev).toOption
    logTime("parseXML")
    ret

  }

  def loadXml(fileName: String, digest: String, stream: InputStream, maxHR: Int, timezone: String): Seq[Move] = {
    val doc = XML.load(stream)

    moveslink.XMLParser.parseXML(fileName, doc, maxHR, timezone).flatMap(_.toOption)
  }

}
