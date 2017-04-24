package com.github.opengrabeso.stravalas

import java.io.InputStream

import com.github.opengrabeso.stravalas.Event.Sport
import net.suunto3rdparty.MoveHeader.ActivityType._
import net.suunto3rdparty._
import scala.collection.immutable.SortedMap

import org.joda.time.{DateTime=>ZonedDateTime}
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
      val stream = hr.stream.mapValues(_.dist)
      DataStreamDist(stream)
    }

    def hrFromHRStream(hr: DataStreamHRWithDist): DataStreamHR = {
      val stream = hr.stream.mapValues(_.hr)
      DataStreamHR(stream)
    }

    val distStream = move.streamGet[DataStreamDist] orElse move.streamGet[DataStreamHRWithDist].map(distFromHRStream)

    val gps = move.streamGet[DataStreamGPS].getOrElse(DataStreamGPS(SortedMap.empty[ZonedDateTime, GPSPoint]))

    val dist = distStream.getOrElse(DataStreamDist(SortedMap.empty[ZonedDateTime, Double]))

    // TODO: other attributes
    val hrStream = move.streamGet[DataStreamHRWithDist].map(hrFromHRStream).getOrElse(DataStreamHR(SortedMap.empty[ZonedDateTime, Int]))

    for {
      startTime <- move.startTime
      endTime <- move.endTime
      d <- dist.stream.lastOption.map(_._2)
    } yield {
      // TODO: digest
      val sport = sportFromActivityType(move.header.moveType)
      val id = Main.ActivityId(0, "", "Activity", startTime, endTime, sport, d)

      val events = Array[Event]() // TODO: basic events

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
