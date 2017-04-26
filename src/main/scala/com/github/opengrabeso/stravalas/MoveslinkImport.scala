package com.github.opengrabeso.stravalas

import java.io.InputStream

import com.github.opengrabeso.stravalas.Event.Sport
import net.suunto3rdparty.MoveHeader.ActivityType._
import net.suunto3rdparty._

import scala.collection.immutable.SortedMap
import org.joda.time.{DateTime => ZonedDateTime}
import DateTimeOps._

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

  def loadFromMove(move: Move): Option[Main.ActivityEvents] = {
    // Move(fileName: Set[String], header: MoveHeader, streams: Map[Class[_], DataStream[_]]) {
    // ActivityEvents(id: ActivityId, events: Array[Event], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream[_]]) {

    def distFromHRStream(hr: DataStreamHRWithDist): DataStreamDist = {
      val stream = hr.mapStreamValues(_.dist)
      DataStreamDist(stream)
    }

    def hrFromHRStream(hr: DataStreamHRWithDist): DataStreamHR = {
      val stream = hr.mapStreamValues(_.hr)
      DataStreamHR(stream)
    }

    val distStream = move.streamGet[DataStreamDist] orElse move.streamGet[DataStreamHRWithDist].map(distFromHRStream)

    val laps = move.streamGet[DataStreamLap]

    val gps = move.streamGet[DataStreamGPS].getOrElse(DataStreamGPS(SortedMap.empty[ZonedDateTime, GPSPoint]))

    val dist = distStream.getOrElse(DataStreamDist(SortedMap.empty[ZonedDateTime, Double]))

    // TODO: other attributes
    val hrStream = move.streamGet[DataStreamHRWithDist].map(hrFromHRStream).getOrElse(DataStreamHR(SortedMap.empty[ZonedDateTime, Int]))

    for {
      startTime <- move.startTime
      endTime <- move.endTime
      d <- dist.stream.lastOption.map(_._2)
    } yield {
      val sport = sportFromActivityType(move.header.moveType)

      // timestamp around 2017 needs 30 bits: (2017-1970)*365*24*3600 = (2017-1970)*365*24*3600
      val startInSec = startTime.getMillis / 1000
      val durationInSec = endTime.getMillis / 1000 - startInSec // 18 b is enough for 3 days - ln(3*24*3600)/ln(2) = 17.98
      val sportId = sport.id // 8b should be enough

      val startBits = 32
      val durationBits = 18
      val sportBits = 8

      val signature = (((startInSec << durationBits) + durationInSec) << sportBits) + sportId

      val id = Main.ActivityId(signature, "", "Activity", startTime, endTime, sport, d)

      val events = Array[Event](BegEvent(id.startTime, sport), EndEvent(id.endTime))

      Main.ActivityEvents(id, events, dist, gps, Seq(hrStream))
    }


  }

  def loadSml(fileName: String, stream: InputStream): Seq[Main.ActivityEvents] = {
    def getDeviceLog(doc: Node): Node = (doc \ "DeviceLog") (0)

    val doc = XML.load(stream)

    val dev = getDeviceLog(doc)

    moveslink2.XMLParser.parseXML(fileName, dev).toOption.toSeq.flatMap(loadFromMove)
  }

  def loadXml(fileName: String, stream: InputStream): Seq[Main.ActivityEvents] = {
    val doc = XML.load(stream)

    moveslink.XMLParser.parseXML(fileName, doc).flatMap(_.toOption).flatMap(loadFromMove)
  }

}
