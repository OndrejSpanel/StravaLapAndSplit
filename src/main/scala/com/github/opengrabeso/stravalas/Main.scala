package com.github.opengrabeso.stravalas

import java.util

import com.google.api.client.http.{GenericUrl, HttpRequest}
import com.google.api.client.http.json.JsonHttpContent
import com.fasterxml.jackson.databind.JsonNode
import org.joda.time.{Period, Seconds, DateTime => ZonedDateTime}

import scala.collection.JavaConverters._
import org.joda.time.format.PeriodFormatterBuilder
import DateTimeOps._
import com.google.api.client.json.jackson2.JacksonFactory
import net.suunto3rdparty._

import scala.collection.immutable.SortedMap

object Main {

  import RequestUtils._

  case class SecretResult(appId: String, appSecret: String, mapboxToken: String, error: String)

  def secret: SecretResult = {
    val filename = "/secret.txt"
    try {
      val secretStream = Main.getClass.getResourceAsStream(filename)
      val lines = scala.io.Source.fromInputStream(secretStream).getLines
      SecretResult(lines.next(), lines.next(), lines.next(), "")
    } catch {
      case _: NullPointerException => // no file found
        SecretResult("", "", "", s"Missing $filename, app developer should check README.md")
      case _: Exception =>
        SecretResult("", "", "", s"Bad $filename, app developer should check README.md")
    }
  }

  case class StravaAuthResult(token: String, mapboxToken: String, id: String, name: String)

  def stravaAuth(code: String): StravaAuthResult = {

    val json = new util.HashMap[String, String]()
    val SecretResult(clientId, clientSecret, mapboxToken, _) = secret

    json.put("client_id", clientId)
    json.put("client_secret", clientSecret)
    json.put("code", code)

    val content = new JsonHttpContent(new JacksonFactory(), json)

    val request = requestFactory.buildPostRequest(new GenericUrl("https://www.strava.com/oauth/token"), content)
    val response = request.execute() // TODO: async?

    val responseJson = jsonMapper.readTree(response.getContent)
    val token = responseJson.path("access_token").textValue

    val athleteJson = responseJson.path("athlete")
    val id = athleteJson.path("id").numberValue.toString
    val name = athleteJson.path("firstname").textValue + " " + athleteJson.path("lastname").textValue

    StravaAuthResult(token, mapboxToken, id, name)

  }

  def authorizeHeaders(request: HttpRequest, authToken: String) = {
    val headers = request.getHeaders
    headers.put("Authorization:", s"Bearer $authToken")
  }

  case class ActivityId(id: Long, name: String, startTime: ZonedDateTime, sportName: String, duration:Int, distance: Double) {

    def secondsInActivity(time: ZonedDateTime): Int = Seconds.secondsBetween(startTime, time).getSeconds

    def endTime: ZonedDateTime = startTime.withDurationAdded(duration, 1000)

    def link: String = s"https://www.strava.com/activities/$id"
  }

  object ActivityId {
    def load(json: JsonNode): ActivityId = {
      // https://strava.github.io/api/v3/activities/
      val name = json.path("name").textValue
      val id = json.path("id").longValue
      val time = ZonedDateTime.parse(json.path("start_date").textValue)
      val sportName = json.path("type").textValue
      val duration = json.path("elapsed_time").intValue
      val distance = json.path("distance").doubleValue

      ActivityId(id, name, time, sportName, duration, distance)
    }
  }

  def lastActivities(authToken: String): Array[ActivityId] = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, authToken, "per_page=15")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    (0 until responseJson.size).map(i => ActivityId.load(responseJson.get(i)))(collection.breakOut)
  }

  case class ActivityEvents(id: ActivityId, events: Array[Event], sports: Array[String], times: Seq[ZonedDateTime], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream[_]]) {

    def secondsInActivity(time: ZonedDateTime): Int  = id.secondsInActivity(time)

    private def convertGPSToPair(gps: GPSPoint) = (gps.latitude, gps.longitude)

    def begPos: (Double, Double) = convertGPSToPair(gps.stream.head._2)
    def endPos: (Double, Double) = convertGPSToPair(gps.stream.last._2)

    def lat: Double = (begPos._1 + endPos._1) * 0.5
    def lon: Double = (begPos._2 + endPos._2) * 0.5

    lazy val relTimes = times.map(secondsInActivity)

    lazy val mapTimeToDist: Map[Int, Double] = {
      def scanTime(now: Int, timeDist: Seq[(Int, Double)], ret: Seq[(Int, Double)]): Seq[(Int, Double)] = {
        timeDist match {
          case head +: tail =>
            if (now >= head._1) {
              scanTime(now + 1, tail, head +: ret)
            } else {
              scanTime(now + 1, timeDist, (now -> head._2) +: ret)
            }
          case Seq() =>
            ret
        }
      }

      val timeRelStamps = distWithRelTimes

      val filledTime = scanTime(0, timeRelStamps, Nil)

      filledTime.toMap
    }

    private def distWithRelTimes = dist.stream.toList.map(t => secondsInActivity(t._1) -> t._2)
    private def gpsWithRelTimes = gps.stream.toList.map(t => secondsInActivity(t._1) -> t._2)

    def distanceForTime(time: ZonedDateTime): Double = {
      val relTime = Seconds.secondsBetween(id.startTime, time).getSeconds
      mapTimeToDist(relTime)
    }

    def routeJS: String = {
      // TODO: distances may have different times than GPS
      (gps.stream.values zip distWithRelTimes).map { case (GPSPoint(lng, lat, _),(t, d)) =>

        s"[$lat,$lng,$t,$d]"
      }.mkString("[\n", ",\n", "]\n")
    }

    def merge(that: ActivityEvents): ActivityEvents = ???

    def editableEvents: Array[EditableEvent] = {

      def neq(a: String, b: String) = a != b
      val sportChange = (("" +: sports) zip sports).map((neq _). tupled)

      val ees = (events, events.drop(1) :+ events.last, sports zip sportChange).zipped.map { case (e1, e2, (sport,change)) =>
        val action = if (change) "split" else e1.defaultEvent
        EditableEvent(action, id.secondsInActivity(e1.stamp), distanceForTime(e1.stamp), sport)
      }

      // consolidate mutliple events with the same time so that all of them have the same action
      val merged = ees.groupBy(_.time).map { case (t, es) =>
        object CmpEvent extends Ordering[String] {
          def compare(x: String, y: String): Int = {
            def score(et: String) = et match {
              case "split" => 1
              case "splitRun" => 2
              case "splitRide" => 3
              case "splitSwim" => 4
              case _ => 0
            }
            score(x) - score(y)
          }
        }
        (t, es.map(_.action).max(CmpEvent))
      }

      ees.map { e => e.copy(action = merged(e.time))}

    }

    def split(splitTime: Int): Option[ActivityEvents] = {

      val splitEvents = events.filter(_.isSplit).toSeq

      val splitTimes = splitEvents.map(e => e.stamp)

      assert(splitTimes.contains(id.startTime))
      assert(splitTimes.contains(id.endTime))

      val splitRanges = splitEvents zip splitTimes.tail

      val toSplit = splitRanges.find(t => secondsInActivity(t._2) == splitTime)

      toSplit.map { case (beg, endTime) =>


        val begTime = beg.stamp


        val eventsRange = (events zip sports).dropWhile(_._1.stamp <= begTime).takeWhile(_._1.stamp < endTime)

        val indexBeg = times.lastIndexWhere(_ <= begTime) max 0

        def safeIndexWhere[T](seq: Seq[T])(pred: T => Boolean) = {
          val i = seq.indexWhere(pred)
          if (i < 0) seq.size else i
        }
        val indexEnd = safeIndexWhere(times)(_ > endTime)

        val timesRange = times.slice(indexBeg, indexEnd)
        val distRange = dist.pickData(dist.slice(indexBeg, indexEnd).stream)
        val gpsRange = gps.pickData(gps.slice(indexBeg, indexEnd).stream)

        val attrRange = attributes.map { attr =>
          attr.slice(indexBeg, indexEnd)
        }

        val act = ActivityEvents(id.copy(startTime = begTime), eventsRange.map(_._1), eventsRange.map(_._2), timesRange, distRange, gpsRange, attrRange)

        act
      }
    }
  }

  trait ActivityStreams {
    def time: Vector[Int]

    def dist: Vector[Double]

    def latlng: Vector[(Double, Double)]

    def attributes: Seq[(String, Seq[Int])]
  }


  def processActivityStream(actId: ActivityId, act: ActivityStreams, laps: List[ZonedDateTime], segments: Seq[Event]): ActivityEvents = {

    // TODO: provide activity type with the split
    val events = (BegEvent(actId.startTime) +: EndEvent(actId.endTime) +: laps.map(LapEvent)) ++ segments

    val eventsByTime = events.sortBy(_.stamp)

    val sports = eventsByTime.map(x => actId.sportName)

    val times = act.time.map(t => actId.startTime.withDurationAdded(t, 1000))

    val dist = new DataStreamDist(SortedMap(times zip act.dist:_*))
    val latlng = new DataStreamGPS(SortedMap(times zip act.latlng.map(ll => GPSPoint(ll._1, ll._2, None)):_*))

    val attr = act.attributes.collect { case (name, aa) if name == "heartrate" =>
      new DataStreamHR(SortedMap(times zip aa:_*))
    }

    ActivityEvents(actId, eventsByTime.toArray, sports.toArray, times, dist, latlng, attr)
  }

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = ActivityId.load(responseJson)
    val startDateStr = responseJson.path("start_date").textValue
    val startTime = ZonedDateTime.parse(startDateStr)

    object StravaActivityStreams extends ActivityStreams {
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

      val attributes: Seq[(String, Seq[Int])] = Seq(
        getAttribByName("heartrate"),
        getAttribByName("cadence"),
        getAttribByName("watts"),
        getAttribByName("temp")
      )

    }

    val laps = {


      val requestLaps = buildGetRequest(s"https://www.strava.com/api/v3/activities/$id/laps", authToken, "")

      val response = requestLaps.execute().getContent

      val lapsJson = jsonMapper.readTree(response)

      val lapTimes = (for (lap <- lapsJson.elements.asScala) yield {
        val lapTimeStr = lap.path("start_date").textValue
        ZonedDateTime.parse(lapTimeStr)
      }).toList


      lapTimes.filter(_ > actId.startTime)
    }

    val segments: Seq[Event] = {
      val segmentList = responseJson.path("segment_efforts").asScala.toList
      segmentList.flatMap { seg =>
        val segStartTime = ZonedDateTime.parse(seg.path("start_date").textValue)
        val segName = seg.path("name").textValue
        val segDuration = seg.path("elapsed_time").intValue
        val segPrivate = seg.path("segment").path("private").booleanValue
        Seq(
          StartSegEvent(segName, segPrivate, segStartTime),
          EndSegEvent(segName, segPrivate, segStartTime.withDurationAdded(segDuration, 1000))
        )
      }
    }


    processActivityStream(actId, StravaActivityStreams, laps, segments)

  }

  def adjustEvents(events: ActivityEvents, eventsInput: Array[String]): ActivityEvents = {
    val ee = events.events zip eventsInput

    val lapsAndSplits: Array[Event] = ee.flatMap { case (e, ei) =>
      ei match {
        case "lap" => Some(LapEvent(e.stamp))

        case "split" => Some(SplitEvent(e.stamp))
        case "end" => Some(EndEvent(e.stamp))
        case "splitSwim" => Some(SplitEvent(e.stamp))
        case "splitRun" => Some(SplitEvent(e.stamp))
        case "splitRide" => Some(SplitEvent(e.stamp))
        case _ => None
      }
    }
    events.copy(events = lapsAndSplits)
  }

  def displaySeconds(duration: Int): String = {
    val myFormat =
      new PeriodFormatterBuilder()
        .printZeroNever().appendHours()
        .appendSeparator(":")
        .printZeroAlways().minimumPrintedDigits(2).appendMinutes()
        .appendSeparator(":")
        .printZeroAlways().minimumPrintedDigits(2).appendSeconds()
        .toFormatter

    val period = Period.seconds(duration).normalizedStandard()
    myFormat.print(period)
  }

  def displayDistance(dist: Double): String = "%.2f".format(dist*0.001)
}





