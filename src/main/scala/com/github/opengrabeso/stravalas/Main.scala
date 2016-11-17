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

  case class ActivityEvents(id: ActivityId, events: Array[Event], sports: Array[String], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream[_]]) {

    assert(events.forall(_.stamp >= id.startTime))
    assert(events.forall(_.stamp <= id.endTime))

    assert(events.forall(_.stamp <= id.endTime))

    assert(gps.inTimeRange(id.startTime, id.endTime))
    assert(dist.inTimeRange(id.startTime, id.endTime))
    assert(attributes.forall(_.inTimeRange(id.startTime, id.endTime)))

    def secondsInActivity(time: ZonedDateTime): Int  = id.secondsInActivity(time)

    private def convertGPSToPair(gps: GPSPoint) = (gps.latitude, gps.longitude)

    def begPos: (Double, Double) = convertGPSToPair(gps.stream.head._2)
    def endPos: (Double, Double) = convertGPSToPair(gps.stream.last._2)

    def lat: Double = (begPos._1 + endPos._1) * 0.5
    def lon: Double = (begPos._2 + endPos._2) * 0.5

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

    def merge(that: ActivityEvents): ActivityEvents = {
      // select some id (name, sport ...)
      val begTime = Seq(id.startTime, that.id.startTime).min
      val endTime = Seq(id.endTime, that.id.endTime).max
      val duration = Seconds.secondsBetween(begTime, endTime).getSeconds

      val mergedId = ActivityId(id.id, id.name, begTime, id.sportName, duration, id.distance + that.id.distance)

      // TODO: merge events properly
      val eventsAndSports = (events zip sports) ++ (that.events zip that.sports)
      // keep only first start Event, change other to Split only

      val (begs, others) = eventsAndSports.partition(_._1.isInstanceOf[BegEvent])
      val (ends, rest) = others.partition(_._1.isInstanceOf[EndEvent])

      val begsSorted = begs.sortBy(_._1.stamp)
      val begsAdjusted = begsSorted.take(1) ++ begsSorted.drop(1).map(e => SplitEvent(e._1.stamp) -> e._2)

      val eventsAndSportsSorted = (begsAdjusted ++ rest :+ ends.maxBy(_._1.stamp)).sortBy(_._1.stamp)

      val mergedGPS = gps.pickData(gps.stream ++ that.gps.stream)

      val (distFirst, distSecond) = if (dist.stream.head._1 < that.dist.stream.head._1) (dist, that.dist) else (that.dist, dist)

      val offsetSecond = distFirst.stream.lastOption.map(last => distSecond.offsetDist(last._2)).getOrElse(distSecond)

      val mergedDist = dist.pickData(distFirst.stream ++ offsetSecond.stream)
      val mergedAttr = attributes.map { a =>
        val aThat = that.attributes.find(_.streamType == a.streamType)
        val aStream = aThat.map(a.stream ++ _.stream).getOrElse(a.stream)
        a.pickData(aStream.asInstanceOf[a.DataMap])
      }
      val notMergedFromThat = that.attributes.find(ta => !attributes.exists(_.streamType == ta.streamType))
      // if something was not merged,

      ActivityEvents(mergedId, eventsAndSportsSorted.map(_._1), eventsAndSportsSorted.map(_._2), mergedDist, mergedGPS, mergedAttr ++ notMergedFromThat)
    }

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

      val toSplit = splitRanges.find(t => secondsInActivity(t._1.stamp) == splitTime)

      toSplit.map { case (beg, endTime) =>


        val begTime = beg.stamp


        val eventsRange = (events zip sports).dropWhile(_._1.stamp <= begTime).takeWhile(_._1.stamp < endTime)

        val distRange = dist.pickData(dist.slice(begTime, endTime).stream)
        val gpsRange = gps.pickData(gps.slice(begTime, endTime).stream)

        val attrRange = attributes.map { attr =>
          attr.slice(begTime, endTime)
        }

        val act = ActivityEvents(id.copy(startTime = begTime), eventsRange.map(_._1), eventsRange.map(_._2), distRange, gpsRange, attrRange)

        act
      }
    }
  }

  trait ActivityStreams {
    def dist: DataStreamDist

    def latlng: DataStreamGPS

    def attributes: Seq[DataStream[_]]
  }


  def processActivityStream(actId: ActivityId, act: ActivityStreams, laps: Seq[ZonedDateTime], segments: Seq[Event]): ActivityEvents = {

    val cleanLaps = laps.filter(l => l > actId.startTime && l < actId.endTime)

    val distStream = act.latlng.distStream

    val smoothingSec = 10
    val speedStream = DataStreamGPS.computeSpeedStream(distStream, smoothingSec)
    val speedMap = SortedMap(speedStream:_*)

    // integrate route distance back from smoothed speed stream so that we are processing consistent data
    val routeDistance = SortedMap(DataStreamGPS.routeStreamFromSpeedStream(speedStream):_*)

    // find pause candidates: times when smoothed speed is very low
    val speedPauseMax = 0.7
    val speedPauseAvg = 0.2

    // select samples which are slow and the following is also slow (can be in the middle of the pause)
    type PauseStream = Seq[(ZonedDateTime, ZonedDateTime, Double)]
    val pauseSpeeds: PauseStream = (speedStream zip speedStream.drop(1)).collect {
      case ((t1, s1), (t2, s2)) if s1 < speedPauseMax && s2< speedPauseMax => (t1, t2, s1)
    }
    // aggregate pause intervals - merge all
    def mergePauses(pauses: PauseStream, done: PauseStream): PauseStream = {
      pauses match {
        case head +: next +: tail =>
          if (head._2 == next._1) { // extend head with next and repeat
            mergePauses(head.copy(_2 = next._2) +: tail, done)
          } else { // head can no longer be extended, use it, continue processing
            mergePauses(next +: tail, head +: done)
          }
        case _ => pauses ++ done
      }
    }

    val mergedPauses = mergePauses(pauseSpeeds, Nil).reverse

    def avgSpeedDuring(beg: ZonedDateTime, end: ZonedDateTime): Double = {
      val findBeg = routeDistance.to(beg).lastOption
      val findEnd = routeDistance.from(end).headOption
      val avgSpeed = for (b <- findBeg; e <- findEnd) yield {
        val duration = Seconds.secondsBetween(b._1, e._1).getSeconds
        if (duration > 0) (e._2 - b._2) / duration else 0
      }
      avgSpeed.getOrElse(0)
    }

    // take a pause candidate and reduce its size until we get a real pause (or nothing)
    def extractPause(beg: ZonedDateTime, end: ZonedDateTime): Option[(ZonedDateTime, ZonedDateTime)] = {
      if (beg >= end) {
        None
      } else {
        val spd = avgSpeedDuring(beg, end)
        if (spd < speedPauseAvg) Some((beg, end))
        else {
          val spdBeg = speedMap(beg)
          val spdEnd = speedMap(end)
          // heuristic approach: remove a border sample with greater speed
          if (spdBeg > spdEnd) {
            val afterBeg = speedMap.from(beg).tail.head._1
            extractPause(afterBeg, end)
          } else {
            val beforeEnd = speedMap.until(end).last._1
            extractPause(beg, beforeEnd)
          }
        }
      }
    }

    val extractedPauses = mergedPauses.flatMap(p => extractPause(p._1, p._2)).map {case (b, e) =>
      val duration = Seconds.secondsBetween(b, e).getSeconds
      (b, e, duration)
    }

    val minPause = 10
    val minLongPause = 20
    val minSportChangePause = 50

    val pauseEvents = extractedPauses.flatMap { case (tBeg, tEnd, duration) =>
      if (duration > minLongPause) {
        Seq(PauseEvent(duration, tBeg), PauseEndEvent(duration, tEnd))
      } else if (duration > minPause) {
        Seq(PauseEvent(duration, tBeg))
      } else Seq()
    }

    val sportChangePauses = extractedPauses.collect {
      case (tBeg, tEnd, duration) if duration > minSportChangePause => (tBeg, tEnd)
    }

    val sportChangeTimes = sportChangePauses.flatMap(p => Seq(p._1, p._2))

    val intervalTimes = (actId.startTime +: sportChangeTimes :+ actId.endTime).distinct

    def speedDuringInterval(beg: ZonedDateTime, end: ZonedDateTime) = {
      speedMap.from(beg).to(end)
    }

    val intervals = intervalTimes zip intervalTimes.drop(1)

    val sportsInRanges = intervals.flatMap { case (pBeg, pEnd) =>

      assert(pEnd > pBeg)
      if (sportChangePauses.exists(_._1 == pBeg)) {
        None // no sport detection during pauses (would always detect as something slow, like Run
      } else {

        val spd = speedDuringInterval(pBeg, pEnd)

        val (avg, fast, max) = DataStreamGPS.speedStats(spd.toSeq)

        def paceToKmh(pace: Double) = 60 / pace

        def kmh(speed: Double) = speed

        def detectSport(maxRun: Double, fastRun: Double, avgRun: Double): String = {
          if (avg <= avgRun && fast <= fastRun && max <= maxRun) "Run"
          else "Ride"
        }

        val sport = actId.sportName.toLowerCase match {
          case "run" =>
            // marked as run, however if clearly contradicting evidence is found, make it ride
            detectSport(paceToKmh(2), paceToKmh(2.5), paceToKmh(3)) // 2 - 3 min/km possible
          case "ride" =>
            detectSport(kmh(20), kmh(17), kmh(15)) // 25 - 18 km/h possible
          case _ =>
            detectSport(paceToKmh(3), paceToKmh(4), paceToKmh(4)) // 3 - 4 min/km possible
          // TODO: handle other sports: swimming, walking, ....
        }
        Some(pBeg, sport)
      }
    }

    // reversed, as we will be searching for last lower than
    val sportsByTime = sportsInRanges.sortBy(_._1)(Ordering[ZonedDateTime].reverse)

    def findSport(time: ZonedDateTime) = {
      sportsByTime.find(_._1 <= time).map(_._2).getOrElse(actId.sportName)
    }

    val events = (BegEvent(actId.startTime) +: EndEvent(actId.endTime) +: cleanLaps.map(LapEvent)) ++ segments ++ pauseEvents
    val eventsByTime = events.sortBy(_.stamp)

    val sports = eventsByTime.map(x => findSport(x.stamp))

    ActivityEvents(actId, eventsByTime.toArray, sports.toArray, act.dist, act.latlng, act.attributes)
  }

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = ActivityId.load(responseJson)
    val startDateStr = responseJson.path("start_date").textValue
    val startTime = ZonedDateTime.parse(startDateStr)

    object StravaActivityStreams extends ActivityStreams {
      // https://strava.github.io/api/v3/streams/
      //private val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")
      private val wantStreams = Seq("time", "latlng", "distance", "altitude", "heartrate" /*, "cadence", "watts", "temp"*/)

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
        GPSPoint(lat, lng, None)
      }

      val timeRelValues = getDataByName("time", _.asInt)
      val distValues = getDataByName("distance", _.asDouble)
      val latlngValues = getDataByName("latlng", loadGpsPair)
      val altValues = getDataByName("altitude", _.asDouble)

      val latLngAltValues = if (altValues.isEmpty) latlngValues else {
        (latlngValues zip altValues).map { case (gps,alt) =>
            gps.copy(elevation = Some(alt.toInt))
        }
      }

      val timeValues = timeRelValues.map ( t => startTime.withDurationAdded(t, 1000))

      val attributeValues: Seq[(String, Seq[Int])] = Seq(
        getAttribByName("cadence"),
        getAttribByName("watts"),
        getAttribByName("temp"),
        getAttribByName("heartrate")
      )

      val dist = DataStreamDist(SortedMap(timeValues zip distValues:_*))
      val latlng = DataStreamGPS(SortedMap(timeValues zip latLngAltValues:_*))
      val attributes =  attributeValues.flatMap { case (name, values) =>
          name match {
            case "heartrate" => Some(new DataStreamHR(SortedMap(timeValues zip values:_*)))
            case _ => None
          }
      }

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





