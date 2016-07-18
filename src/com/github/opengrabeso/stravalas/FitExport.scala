package com.github.opengrabeso.stravalas

import com.garmin.fit._
import Main.ActivityEvents
import com.garmin.fit
import DateTimeOps._
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

    def encodeLatLng(msg: RecordMesg, latlng: (Double, Double)) = {
      val longLatScale = (1L << 31).toDouble / 180
      msg.setPositionLat((latlng._1 * longLatScale).toInt)
      msg.setPositionLong((latlng._2 * longLatScale).toInt)

    }

    class GPSEvent(val time: JodaDateTime, val lat: Double, val lng: Double) extends DataEvent(time, encodeLatLng(_, (lat, lng)))

    class AttribEvent(val time: JodaDateTime, data: Int, set: (RecordMesg, Int) => Unit) extends DataEvent(time, set(_, data))

    import events.id.startTime

    val gpsAsEvents = events.gps.zipWithIndex.map { case (gps, i) =>
      new GPSEvent(startTime.plusSeconds(i), gps._1, gps._2)
    }

    val attributesAsEvents = events.attributes.flatMap { case (name, attrib) =>
      val createAttribEvent: (RecordMesg, Int) => Unit = (msg, value) =>
        name match {
          case "heartrate" => msg.setHeartRate(value.toShort)
          case "watts" => msg.setPower(value)
          case "cadence" => msg.setCadence(value.toShort)
          case "temp" => msg.setTemperature(value.toByte)
        }
      attrib.zipWithIndex.map { case (data, i) =>
        new AttribEvent(startTime.plusSeconds(i), data, createAttribEvent)
      }
    }

    object LapAutoClose {
      var openLap = false
      var lapCounter = 0
      var lastLapStart = events.id.startTime

      def closeLap(time: JodaDateTime): Unit = {
        if (openLap && time > lastLapStart) {
          val myMsg = new LapMesg()
          myMsg.setEvent(Event.LAP)
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
      myMsg.setEvent(Event.ACTIVITY)
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
        Some(new LapEvent(startTime.plusSeconds(time.time)))
      case _ =>
        None
    }

    val allEvents = (gpsAsEvents ++ attributesAsEvents ++ lapsAsEvents).toVector.sortBy(_.time)

    LapAutoClose.closeLap(allEvents.head.time)
    allEvents.foreach(_.encode(encoder))
    LapAutoClose.closeLap(allEvents.last.time)

    val timeBeg = events.id.startTime
    val durationSec = events.gps.size
    val timeEnd = events.id.startTime.plusSeconds(durationSec)

    val (sport, subsport) = events.id.sportName.toLowerCase match {
      case "run" => (Sport.RUNNING, SubSport.STREET)
      case "ride" => (Sport.CYCLING, SubSport.ROAD)
      case "swim" => (Sport.SWIMMING, SubSport.LAP_SWIMMING)
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

      myMsg.setEvent(Event.SESSION)
      myMsg.setEventType(EventType.STOP)

      encoder.onMesg(myMsg)
    }

    closeActivity(timeEnd)

    encoder.close
  }


}
