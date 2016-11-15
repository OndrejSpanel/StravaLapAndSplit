package com.github.opengrabeso.stravalas

import java.io.InputStream

import com.garmin.fit._
import com.github.opengrabeso.stravalas.Main.ActivityEvents
import org.joda.time.{Seconds, DateTime => JodaDateTime, Duration => JodaDuration}
import DateTimeOps._
import net.suunto3rdparty.{DataStreamDist, DataStreamGPS, DataStreamHR, GPSPoint}

import scala.collection.immutable.SortedMap
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Ondra on 20.10.2016.
  */
object FitImport {

  def fromTimestamp(dateTime: DateTime): JodaDateTime = {
    new JodaDateTime(dateTime.getDate.getTime)
  }

  def fromTimestamp(timeMs: Long): JodaDateTime = {
    new JodaDateTime(timeMs + DateTime.OFFSET)
  }

  def decodeLatLng(latlng: (Int, Int)): GPSPoint = {
    val longLatScale = (1L << 31).toDouble / 180
    GPSPoint(latlng._1 / longLatScale, latlng._2 / longLatScale, None)
  }


  def apply(in: InputStream): Option[ActivityEvents] = {
    val decode = new Decode
    try {

      val gpsBuffer =  ArrayBuffer[(JodaDateTime, GPSPoint)]() // Time -> Lat / Long
      val hrBuffer =  ArrayBuffer[(JodaDateTime, Int)]()
      val distanceBuffer = ArrayBuffer[(JodaDateTime, Double)]()

      val listener = new MesgListener {

        override def onMesg(mesg: Mesg): Unit = {
          if (mesg.getNum == MesgNum.RECORD) {
            val timestamp = Option(mesg.getField(RecordMesg.TimestampFieldNum)).map(_.getLongValue)
            val heartrate = Option(mesg.getField(RecordMesg.HeartRateFieldNum)).map(_.getIntegerValue)
            val distance = Option(mesg.getField(RecordMesg.DistanceFieldNum)).map(_.getFloatValue)
            val posLat = Option(mesg.getField(RecordMesg.PositionLatFieldNum)).map(_.getIntegerValue)
            val posLong = Option(mesg.getField(RecordMesg.PositionLongFieldNum)).map(_.getIntegerValue)

            for (time <- timestamp) {
              // time may be seconds or miliseconds, how to know?
              val smartTime: Long = if (time < 1e10) time * 1000 else time

              val jTime = fromTimestamp(smartTime)
              for (lat <- posLat; long <- posLong) {
                gpsBuffer += jTime -> decodeLatLng(lat, long)
              }
              // TODO: process elevation if possible
              for (hr <- heartrate) {
                hrBuffer += jTime -> hr
              }
              for (d <- distance) {
                distanceBuffer += jTime -> d.toDouble
              }
            }
          }
        }
      }

      decode.read(in, listener)

      val gpsStream = SortedMap(gpsBuffer:_*)
      val hrStream = SortedMap(hrBuffer:_*)

      val startTime = gpsStream.head._1
      val duration = Seconds.secondsBetween(startTime, gpsStream.last._1).getSeconds

      // TODO: use provided distance when available instead of computing it again
      val distanceToUse = if (distanceBuffer.nonEmpty) {
        SortedMap(distanceBuffer:_*)
      } else {

        val m = (gpsStream.toSeq zip gpsStream.drop(1).toSeq).map { case ((_, gps1), (t2, gps2)) =>
          t2 -> GPS.distance(gps1.latitude, gps1.longitude, gps2.latitude, gps1.longitude)
        }
        val distanceDeltas = SortedMap(m:_*)

        val distanceValues = distanceDeltas.scanLeft(0d) { case (dist, (t, d)) => dist + d }

        val distances = ((distanceDeltas + (startTime -> 0d)) zip distanceValues).map { case ((t, _), dist) => t -> dist }

        distances
      }

      val id = Main.ActivityId(0, "Activity", startTime, "Ride", duration, distanceToUse.last._2)

      object ImportedStreams extends Main.ActivityStreams {

        val dist = new DataStreamDist(distanceToUse)

        val latlng = new DataStreamGPS(gpsStream)

        def attributes = Seq(
          // TODO: cadence, temperature and other attributes
          new DataStreamHR(hrStream)
        )

      }

      // TODO: laps
      Some(Main.processActivityStream(id, ImportedStreams, Nil, Nil))

    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        None
    } finally {
      in.close()
    }

  }
}
