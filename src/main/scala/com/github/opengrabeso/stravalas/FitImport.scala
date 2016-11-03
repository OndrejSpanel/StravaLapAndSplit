package com.github.opengrabeso.stravalas

import java.io.InputStream

import com.garmin.fit._
import com.github.opengrabeso.stravalas.Main.ActivityEvents
import org.joda.time.{DateTime => JodaDateTime, Duration => JodaDuration}
import DateTimeOps._

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

  def decodeLatLng(latlng: (Int, Int)): (Double, Double) = {
    val longLatScale = (1L << 31).toDouble / 180
    (latlng._1 / longLatScale, latlng._2 / longLatScale)
  }


  def apply(in: InputStream): Option[ActivityEvents] = {
    val decode = new Decode
    try {

      val gpsBuffer =  ArrayBuffer[(JodaDateTime, (Double, Double))]() // Time -> Lat / Long
      val hrBuffer =  ArrayBuffer[(JodaDateTime, Int)]()

      // TODO: use provided distance when available instead of computing it again
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
              for (hr <- heartrate) {
                hrBuffer += jTime -> hr
              }
            }
          }
        }
      }

      decode.read(in, listener)

      val gpsStream = SortedMap[JodaDateTime, (Double, Double)](gpsBuffer:_*)
      val hrStream = SortedMap[JodaDateTime, Int](hrBuffer:_*)

      val startTime = gpsStream.head._1

      val distanceDeltas = (gpsStream zip gpsStream.drop(1)).map { case ((t1, gps1), (t2, gps2)) =>
          t2 -> GPS.distance(gps1._1, gps1._2, gps2._1, gps1._2)
      }

      val distanceValues = distanceDeltas.scanLeft(0d) { case (dist, (t, d)) => dist + d}

      val distances = ((distanceDeltas  + (startTime -> 0d)) zip distanceValues).map { case ((t, _), dist) => t -> dist}

      val times = distances.map { case (t, d) =>
        new JodaDuration(startTime, t).getStandardSeconds.toInt
      }

      val id = Main.ActivityId(0, "Activity", startTime, "Ride", times.last, distances.last._2)

      object ImportedStreams extends Main.ActivityStreams {
        val time = times.toVector

        val dist = distances.values.toVector

        val latlng = gpsStream.values.toVector

        def attributes = Seq(
          // TODO: cadence, temperature and other attributes
          ("heartrate", hrStream.values.toVector)
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
