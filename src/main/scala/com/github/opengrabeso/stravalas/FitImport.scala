package com.github.opengrabeso.stravalas

import java.io.InputStream

import com.garmin.fit._
import com.github.opengrabeso.stravalas.Main.ActivityEvents
import org.joda.time.{Seconds, DateTime => ZonedDateTime}
import DateTimeOps._
import net.suunto3rdparty.{DataStreamDist, DataStreamGPS, DataStreamHR, GPSPoint}

import scala.collection.immutable.SortedMap
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Ondra on 20.10.2016.
  */
object FitImport {

  private def fromTimestamp(dateTime: DateTime): ZonedDateTime = {
    new ZonedDateTime(dateTime.getDate.getTime)
  }

  private def fromTimestamp(timeMs: Long): ZonedDateTime = {
    val smartTime = if (timeMs < 1e10) timeMs * 1000 else timeMs
    new ZonedDateTime(smartTime + DateTime.OFFSET)
  }

  private def decodeLatLng(lat: Int, lng: Int, elev: Option[java.lang.Float]): GPSPoint = {
    val longLatScale = (1L << 31).toDouble / 180
    GPSPoint(lat / longLatScale, lng / longLatScale, elev.map(_.toInt))
  }


  def apply(in: InputStream): Option[ActivityEvents] = {
    val decode = new Decode
    try {

      val gpsBuffer = ArrayBuffer[(ZonedDateTime, GPSPoint)]() // Time -> Lat / Long
      val hrBuffer = ArrayBuffer[(ZonedDateTime, Int)]()
      val distanceBuffer = ArrayBuffer[(ZonedDateTime, Double)]()
      val lapBuffer=ArrayBuffer[ZonedDateTime]()

      val listener = new MesgListener {

        override def onMesg(mesg: Mesg): Unit = {
          mesg.getNum match {
            case MesgNum.RECORD =>
              val timestamp = Option(mesg.getField(RecordMesg.TimestampFieldNum)).map(_.getLongValue)
              val heartrate = Option(mesg.getField(RecordMesg.HeartRateFieldNum)).map(_.getIntegerValue)
              val distance = Option(mesg.getField(RecordMesg.DistanceFieldNum)).map(_.getFloatValue)
              val posLat = Option(mesg.getField(RecordMesg.PositionLatFieldNum)).map(_.getIntegerValue)
              val posLong = Option(mesg.getField(RecordMesg.PositionLongFieldNum)).map(_.getIntegerValue)
              val elev = Option(mesg.getField(RecordMesg.AltitudeFieldNum)).map(_.getFloatValue)

              for (time <- timestamp) {
                // time may be seconds or miliseconds, how to know?
                val jTime = fromTimestamp(time)
                for (lat <- posLat; long <- posLong) {
                  gpsBuffer += jTime -> decodeLatLng(lat, long, elev)
                }

                for (hr <- heartrate) {
                  hrBuffer += jTime -> hr
                }
                for (d <- distance) {
                  distanceBuffer += jTime -> d.toDouble
                }
              }
            case MesgNum.LAP =>
              val timestamp = Option(mesg.getField(RecordMesg.TimestampFieldNum)).map(_.getLongValue)
              for (time <- timestamp) {
                lapBuffer += fromTimestamp(time)
              }
            case _ =>

          }
        }
      }

      decode.read(in, listener)

      val gpsStream = SortedMap(gpsBuffer:_*)
      val hrStream = SortedMap(hrBuffer:_*)

      val startTime = gpsStream.head._1
      val endTime = gpsStream.last._1

      val duration = Seconds.secondsBetween(startTime, gpsStream.last._1).getSeconds

      val gpsDataStream = new DataStreamGPS(gpsStream)
      val distData = if (distanceBuffer.nonEmpty) {
        SortedMap(distanceBuffer:_*)
      } else {
        val distanceDeltas = gpsDataStream.distStream

        val distances = DataStreamGPS.routeStreamFromDistStream(distanceDeltas)

        SortedMap(distances:_*)
      }

      val id = Main.ActivityId(0, "Activity", startTime, "Ride", duration, distData.last._2)

      object ImportedStreams extends Main.ActivityStreams {

        val dist = DataStreamDist(distData)

        val latlng = gpsDataStream

        def attributes = Seq(
          // TODO: cadence, temperature and other attributes
          DataStreamHR(hrStream)
        )

      }

      Some(Main.processActivityStream(id, ImportedStreams, lapBuffer, Nil))

    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        None
    } finally {
      in.close()
    }

  }
}
