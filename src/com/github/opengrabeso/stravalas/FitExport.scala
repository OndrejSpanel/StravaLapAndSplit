package com.github.opengrabeso.stravalas

import com.garmin.fit._
import Main.ActivityEvents
import com.garmin.fit
import DateTimeOps._
import org.joda.time.{DateTime=>JodaDateTime}

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

    case class GPSEvent(time: JodaDateTime, lat: Double, lng: Double) extends FitEvent {
      override def encode(encoder: Encoder): Unit = {
        val myMsg = new RecordMesg()
        val longLatScale = (1L << 31).toDouble / 180
        myMsg.setTimestamp(toTimestamp(time))
        myMsg.setPositionLong((lng * longLatScale).toInt)
        myMsg.setPositionLat((lat * longLatScale).toInt)
        encoder.onMesg(myMsg)

      }
    }

    import events.id.startTime

    val gpsAsEvents = events.gps.zipWithIndex.map { case (gps,i) =>
      GPSEvent(startTime.plusSeconds(i), gps._1, gps._2)
    }

    val allEvents = gpsAsEvents.toVector.sortBy(_.time)

    allEvents.foreach(_.encode(encoder))

    encoder.close
  }


}
