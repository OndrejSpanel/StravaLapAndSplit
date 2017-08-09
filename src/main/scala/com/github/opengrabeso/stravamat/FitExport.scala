package com.github.opengrabeso.stravamat

import com.garmin.fit
import com.garmin.fit.{Event => FitEvent, _}
import Main.ActivityEvents

import shared.Util._
import org.joda.time.{Seconds, DateTime => JodaDateTime}

object FitExport {
  type Encoder = MesgListener with MesgDefinitionListener

  private def createEncoder: BufferEncoder = {
    new BufferEncoder
  }

  def encodeHeader(encoder: Encoder): Unit = {
    //Generate FileIdMessage
    val fileIdMesg = new FileIdMesg
    fileIdMesg.setType(fit.File.ACTIVITY)
    encoder.onMesg(fileIdMesg)
  }

  def toTimestamp(zonedTime: JodaDateTime): DateTime = {
    val instant = zonedTime.toInstant
    val timestamp = instant.getMillis / 1000 - DateTime.OFFSET / 1000.0
    val dateTime = new DateTime(0, timestamp)
    dateTime
  }

  def export(events: ActivityEvents): Array[Byte] = {
    val encoder = createEncoder

    abstract class FitEvent {
      def time: JodaDateTime

      def encode(encoder: Encoder)
    }

    abstract class DataEvent(time: JodaDateTime, set: RecordMesg => Unit) extends FitEvent {
      override def encode(encoder: Encoder): Unit = {
        val myMsg = new RecordMesg()
        myMsg.setTimestamp(toTimestamp(time))
        set(myMsg)
        encoder.onMesg(myMsg)
      }
    }

    def encodeGPS(msg: RecordMesg, gps: GPSPoint) = {
      val longLatScale = (1L << 31).toDouble / 180
      msg.setPositionLong((gps.longitude * longLatScale).toInt)
      msg.setPositionLat((gps.latitude * longLatScale).toInt)
      gps.elevation.foreach(e => msg.setAltitude(e.toFloat))

    }

    class GPSEvent(val time: JodaDateTime, val gps: GPSPoint) extends DataEvent(time, encodeGPS(_, gps))

    class AttribEvent(val time: JodaDateTime, data: Int, set: (RecordMesg, Int) => Unit) extends DataEvent(time, set(_, data))

    val gpsAsEvents = events.gps.stream map { case (t, gps) =>
      new GPSEvent(t, gps)
    }

    val attributesAsEvents = events.attributes.flatMap { attrib =>
      val createAttribEvent: (RecordMesg, Int) => Unit = (msg, value) =>
        attrib match {
          case x: DataStreamHR => msg.setHeartRate(value.toShort)
          //case "watts" => msg.setPower(value)
          //case "cadence" => msg.setCadence(value.toShort)
          //case "temp" => msg.setTemperature(value.toByte)
          case _ => ???
        }
      attrib.stream.map { case (t, data) =>
        new AttribEvent(t, data.asInstanceOf[Int], createAttribEvent)
      }
    }

    object LapAutoClose {
      var openLap = false
      var lapCounter = 0
      var lastLapStart = events.id.startTime

      def closeLap(time: JodaDateTime): Unit = {
        if (openLap && time > lastLapStart) {
          val myMsg = new LapMesg()
          myMsg.setEvent(FitEvent.LAP)
          myMsg.setEventType(EventType.STOP)
          myMsg.setStartTime(toTimestamp(lastLapStart))
          myMsg.setTimestamp(toTimestamp(time))
          myMsg.setMessageIndex(lapCounter)
          val lapDurationSec = Seconds.secondsBetween(lastLapStart, time).getSeconds.toFloat
          lapCounter += 1
          myMsg.setTotalElapsedTime(lapDurationSec)
          myMsg.setTotalTimerTime(lapDurationSec)
          encoder.onMesg(myMsg)
        }
        lastLapStart = time
        openLap = true
      }
    }

    def closeActivity(timeEnd: JodaDateTime): Unit = {
      val myMsg = new ActivityMesg()
      myMsg.setTimestamp(toTimestamp(timeEnd))
      myMsg.setNumSessions(1)
      myMsg.setType(Activity.MANUAL)
      myMsg.setEvent(FitEvent.ACTIVITY)
      myMsg.setEventType(EventType.STOP)
      encoder.onMesg(myMsg)
    }


    class LapEvent(val time: JodaDateTime) extends FitEvent {
      override def encode(encoder: Encoder): Unit = {
        LapAutoClose.closeLap(time)
      }
    }

    val lapsAsEvents = events.events.flatMap {
      case LapEvent(time) =>
        Some(new LapEvent(time))
      case _ =>
        None
    }

    val allEvents = (gpsAsEvents ++ attributesAsEvents ++ lapsAsEvents).toVector.sortBy(_.time)

    val timeBeg = allEvents.head.time
    val timeEnd = allEvents.last.time

    def encodeHeader(encoder: Encoder): Unit = {
      //Generate FileIdMessage
      val fileIdMesg = new FileIdMesg
      fileIdMesg.setManufacturer(Manufacturer.SUUNTO)
      fileIdMesg.setType(fit.File.ACTIVITY)
      fileIdMesg.setProduct(1) // TODO: detect for real
      encoder.onMesg(fileIdMesg)
    }

    encodeHeader(encoder)

    LapAutoClose.closeLap(timeBeg)
    allEvents.foreach(_.encode(encoder))

    val durationSec = Seconds.secondsBetween(timeBeg, timeEnd).getSeconds

    LapAutoClose.closeLap(timeEnd)

    val (sport, subsport) = events.id.sportName match {
      // TODO: handle other sports
      case Event.Sport.Run => (Sport.RUNNING, SubSport.STREET)
      case Event.Sport.Ride => (Sport.CYCLING, SubSport.ROAD)
      case Event.Sport.Swim => (Sport.SWIMMING, SubSport.GENERIC)
      case Event.Sport.Hike => (Sport.HIKING, SubSport.GENERIC)
      case Event.Sport.Walk => (Sport.WALKING, SubSport.GENERIC)
      case Event.Sport.NordicSki => (Sport.CROSS_COUNTRY_SKIING, SubSport.GENERIC)
      case _ => (Sport.GENERIC, SubSport.GENERIC)
    }

    {
      val myMsg = new SessionMesg()
      myMsg.setStartTime(toTimestamp(timeBeg))
      myMsg.setTimestamp(toTimestamp(timeEnd))
      myMsg.setSport(sport)
      myMsg.setSubSport(subsport)
      myMsg.setTotalElapsedTime(durationSec.toFloat)
      myMsg.setTotalTimerTime(durationSec.toFloat)
      myMsg.setMessageIndex(0)
      myMsg.setFirstLapIndex(0)
      myMsg.setNumLaps(LapAutoClose.lapCounter + 1)

      myMsg.setEvent(FitEvent.SESSION)
      myMsg.setEventType(EventType.STOP)

      encoder.onMesg(myMsg)
    }

    closeActivity(timeEnd)

    encoder.close
  }


}
