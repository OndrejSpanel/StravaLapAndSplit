package com.github.opengrabeso.stravalas

import java.io.InputStream

import com.garmin.fit._
import com.github.opengrabeso.stravalas.Main.ActivityEvents
import org.joda.time.{DateTime => JodaDateTime}
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

      val listener = new MesgListener {

        override def onMesg(mesg: Mesg): Unit = {
          if (mesg.getNum == MesgNum.RECORD) {
            val timestamp = Option(mesg.getField(RecordMesg.TimestampFieldNum)).map(_.getLongValue)
            val heartrate = Option(mesg.getField(RecordMesg.HeartRateFieldNum)).map(_.getIntegerValue)
            val distance = Option(mesg.getField(RecordMesg.DistanceFieldNum)).map(_.getFloatValue)
            val posLat = Option(mesg.getField(RecordMesg.PositionLatFieldNum)).map(_.getIntegerValue)
            val posLong = Option(mesg.getField(RecordMesg.PositionLongFieldNum)).map(_.getIntegerValue)

            for (time <- timestamp) {
              val jTime = fromTimestamp(time)
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

      val distStream = (gpsStream zip gpsStream.drop(1)).map { case ((t1, gps1), (t2, gps2)) =>
          GPS.distance(gps1._1, gps1._2, gps2._1, gps1._2)
      }

      val totDist = distStream.sum

      println(gpsStream.size)
      println(hrStream.size)
      println(s"Total distance $totDist")
      // create a common array with stamps and data?
      /*
      val startDateStr = responseJson.path("start_date").textValue
      val startTime = DateTime.parse(startDateStr)

      object ActivityStreams {
        //private val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")
        private val wantStreams = Seq("time", "latlng", "distance", "heartrate", "cadence", "watts", "temp")

        private val streamTypes = wantStreams.mkString(",")

        private val uri = s"https://www.strava.com/api/v3/activities/$id/streams/$streamTypes"
        private val request = buildGetRequest(uri, authToken, "")

        private val response = request.execute().getContent

        private val responseJson = jsonMapper.readTree(response)

        val streams = responseJson.elements.asScala.toIterable

        def getData[T](stream: Stream[JsonNode], get: JsonNode => T): Vector[T] = {
          if (stream.isEmpty) Vector()
          else stream.head.path("data").asScala.map(get).toVector
        }
        def getDataByName[T](name: String, get: JsonNode => T): Vector[T] = {
          val stream = streams.filter(_.path("type").textValue == name).toStream
          getData(stream, get)
        }
        def getAttribByName(name: String): (String, Seq[Int]) = {
          (name, getDataByName(name, _.asInt))
        }

        private def loadGpsPair(gpsItem: JsonNode) = {
          val elements = gpsItem.elements
          val lat = elements.next.asDouble
          val lng = elements.next.asDouble
          (lat, lng)
        }

        val time = getDataByName("time", _.asInt)
        val dist = getDataByName("distance", _.asDouble)
        val latlng = getDataByName("latlng", loadGpsPair)
        val heartrate = getAttribByName("heartrate")
        val cadence = getAttribByName("cadence")
        val watts = getAttribByName("watts")
        val temp = getAttribByName("temp")

        val stamps = (time zip dist).map((Stamp.apply _).tupled)

        def stampForTime(time: Int): Stamp = {
          stamps.filter(_.time >= time).head
        }
      }

*/


    } finally {
      in.close()
    }


    None
  }
}
