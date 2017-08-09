package com.github.opengrabeso.stravamat

import java.security.MessageDigest
import java.util
import java.util.Locale

import com.google.api.client.http.{GenericUrl, HttpRequest}
import com.google.api.client.http.json.JsonHttpContent
import com.fasterxml.jackson.databind.JsonNode
import org.joda.time.{Interval, Period, PeriodType, Seconds, DateTime => ZonedDateTime}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat, PeriodFormatterBuilder}

import scala.collection.JavaConverters._
import shared.Util._
import FileId._
import com.google.api.client.json.jackson2.JacksonFactory

import scala.collection.immutable.SortedMap
import scala.xml.Elem

object Main {

  import RequestUtils._

  private val md = MessageDigest.getInstance("SHA-256")

  def digest(bytes: Array[Byte]): String = {
    val digestBytes = (0:Byte) +: md.digest(bytes) // prepend 0 byte to avoid negative sign
    BigInt(digestBytes).toString(16)
  }

  def digest(str: String): String = digest(str.getBytes)

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

  case class StravaAuthResult(token: String, mapboxToken: String, id: String, name: String) {
    // used to prove user is authenticated, but we do not want to store token in plain text to avoid security leaks
    lazy val userId: String = digest(token)
  }

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

  @SerialVersionUID(11L)
  case class ActivityId(id: FileId, digest: String, name: String, startTime: ZonedDateTime, endTime: ZonedDateTime, sportName: Event.Sport, distance: Double) {

    override def toString = s"${id.toString} - $name ($startTime..$endTime)"

    def secondsInActivity(time: ZonedDateTime): Int = Seconds.secondsBetween(startTime, time).getSeconds

    val duration: Int = Seconds.secondsBetween(startTime, endTime).getSeconds

    def timeOffset(offset: Int): ActivityId = copy(startTime = startTime plusSeconds offset, endTime = endTime plusSeconds offset)


    def isMatching(that: ActivityId): Boolean = {
      // check overlap time

      val commonBeg = Seq(startTime,that.startTime).max
      val commonEnd = Seq(endTime,that.endTime).min
      if (commonEnd > commonBeg) {
        val commonDuration = Seconds.secondsBetween(commonBeg, commonEnd).getSeconds
        commonDuration > (duration min that.duration) * 0.75f
      } else false
    }

    def link: String = {
      id match {
        case StravaId(num) =>
          s"https://www.strava.com/activities/$num"
        case _ =>
          null // not a Strava activity - no link
      }
    }

    def hrefLink: Elem = {
      id match {
        case StravaId(num) =>
          <a href={s"https://www.strava.com/activities/$num"}>{name}</a>
        case FilenameId(filename) =>
          <div>File</div> // TODO: check Quest / GPS filename?
        case _ =>
          <div>{id.toString}</div>
      }
    }
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
      val actDigest = digest(json.toString)

      ActivityId(StravaId(id), actDigest, name, time, time.plusSeconds(duration), Event.Sport.withName(sportName), distance)
    }
  }

  def recentStravaActivities(auth: StravaAuthResult): Seq[ActivityId] = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, auth.token, "per_page=15")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val stravaActivities = (0 until responseJson.size).map { i =>
      val actI = responseJson.get(i)
      ActivityId.load(actI)
    }
    stravaActivities
  }

  def stravaActivitiesNotStaged(auth: StravaAuthResult): Seq[ActivityId] = {
    val stravaActivities = recentStravaActivities(auth)

    val storedActivities = stagedActivities(auth)
    // do not display the activities which are already staged
    stravaActivities diff storedActivities
  }

  object namespace {
    // stage are data visible to the user
    val stage = "stage"
    // file upload progress
    val uploadProgress = "upload-progress"
    // upload - invisible data, used to hand data to the background upload tasks
    def upload(session: String) = "upload-" + session
    // upload results - report upload status and resulting id
    def uploadResult(session: String) = "upload-result-" + session
    // user settings
    val settings = "settings"
  }

  def stagedActivities(auth: StravaAuthResult): Seq[ActivityHeader] = {
    val storedActivities = {
      val d = Storage.enumerate(namespace.stage, auth.userId)
      d.flatMap { a =>
        try {
          Storage.load[ActivityHeader](namespace.stage, a, auth.userId)
        } catch {
          case x: java.io.InvalidClassException => // bad serialVersionUID
            println(s"load error ${x.getMessage} - $a")
            Storage.delete(namespace.stage, a, auth.userId)
            None
          case x: Exception =>
            x.printStackTrace()
            None
        }
      }
    }
    storedActivities.toVector
  }

  @SerialVersionUID(10L)
  case object NoActivity

  @SerialVersionUID(10L)
  case class ActivityHeader(id: ActivityId, hasGPS: Boolean, hasAttributes: Boolean)

  @SerialVersionUID(10L)
  case class ActivityEvents(id: ActivityId, events: Array[Event], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream]) {

    def header: ActivityHeader = ActivityHeader(id, hasGPS, hasAttributes)

    def streams = {
      if (hasGPS) dist +: gps +: attributes
      else dist +: attributes
    }

    def startTime = id.startTime
    def endTime = id.endTime
    def duration: Double = (endTime.getMillis - startTime.getMillis).toDouble / 1000

    def isAlmostEmpty(minDurationSec: Int) = {
      !streams.exists(_.stream.nonEmpty) || endTime < startTime.plusSeconds(minDurationSec) || streams.exists(x => x.isAlmostEmpty)
    }

    override def toString = id.toString
    def toLog: String = streams.map(_.toLog).mkString(", ")

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

    // must call hasGPS because it is called while composing the JS, even when hasGPS is false
    def lat: Double = if (hasGPS) (begPos._1 + endPos._1) * 0.5 else 0.0
    def lon: Double = if (hasGPS) (begPos._2 + endPos._2) * 0.5 else 0.0

    def hasGPS: Boolean = gps.stream.nonEmpty
    def hasAttributes: Boolean = attributes.exists(_.stream.nonEmpty)

    def distanceForTime(time: ZonedDateTime): Double = dist.distanceForTime(time)


    def optimizeRoute: Seq[(ZonedDateTime, GPSPoint)] = {
      // TODO: smart optimization based on direction changes and distances
      val maxPoints = 1000
      if (gps.stream.size < maxPoints) gps.stream.toList
      else {
        val ratio = gps.stream.size / maxPoints
        val gpsSeq = gps.stream.toList

        val groups = gpsSeq.grouped(ratio).toList

        // take each n-th
        val allButLast = groups.dropRight(1).map(_.head)
        // always take the last one
        val lastGroup = groups.last
        val last = if (lastGroup.lengthCompare(1) > 0) lastGroup.take(1) ++ lastGroup.takeRight(1)
        else lastGroup

        allButLast ++ last
      }
    }

    def routeJS: String = {
      val toSend = optimizeRoute

      toSend.map { case (time,g) =>
        val t = id.secondsInActivity(time)
        val d = distanceForTime(time)
        s"[${g.longitude},${g.latitude},$t,$d]"
      }.mkString("[\n", ",\n", "]\n")
    }

    def merge(that: ActivityEvents): ActivityEvents = {
      // select some id (name, sport ...)
      val begTime = Seq(id.startTime, that.id.startTime).min
      val endTime = Seq(id.endTime, that.id.endTime).max

      // TODO: unique ID (merge or hash input ids?)
      val sportName = if (Event.sportPriority(id.sportName) < Event.sportPriority(that.id.sportName)) id.sportName else that.id.sportName

      val mergedId = ActivityId(TempId(id.id.filename), "", id.name, begTime, endTime, sportName, id.distance + that.id.distance)

      val eventsAndSports = (events ++ that.events).sortBy(_.stamp)

      // keep only first start Event, change other to Split only
      val (begs, others) = eventsAndSports.partition(_.isInstanceOf[BegEvent])
      val (ends, rest) = others.partition(_.isInstanceOf[EndEvent])

      val begsSorted = begs.sortBy(_.stamp).map(_.asInstanceOf[BegEvent])
      val begsAdjusted = begsSorted.take(1) ++ begsSorted.drop(1).map(e => SplitEvent(e.stamp, e.sport))

      val eventsAndSportsSorted = (begsAdjusted ++ rest :+ ends.maxBy(_.stamp)).sortBy(_.stamp)

      val mergedGPS = gps.pickData(gps.stream ++ that.gps.stream)

      val (distFirst, distSecond) = if (dist.stream.head._1 < that.dist.stream.head._1) (dist, that.dist) else (that.dist, dist)

      val offsetSecond = if (distSecond.stream.nonEmpty && distFirst.stream.nonEmpty) {
        val offset = distFirst.distanceForTime(distSecond.stream.head._1)
        distSecond.offsetDist(offset)
      } else {
        distSecond
      }

      val mergedDist = dist.pickData(distFirst.stream ++ offsetSecond.stream)
      val mergedAttr = attributes.map { a =>
        val aThat = that.attributes.find(_.streamType == a.streamType)
        val aStream = aThat.map(a.stream ++ _.stream).getOrElse(a.stream)
        a.pickData(aStream.asInstanceOf[a.DataMap])
      }
      val notMergedFromThat = that.attributes.find(ta => !attributes.exists(_.streamType == ta.streamType))
      // if something was not merged,

      ActivityEvents(mergedId, eventsAndSportsSorted, mergedDist, mergedGPS, mergedAttr ++ notMergedFromThat)
    }

    def editableEvents: Array[EditableEvent] = {

      val ees = events.map { e1 =>
        val action = e1.defaultEvent
        EditableEvent(action, id.secondsInActivity(e1.stamp), distanceForTime(e1.stamp), e1.listTypes)
      }

      // consolidate mutliple events with the same time so that all of them have the same action
      val merged = ees.groupBy(_.time).map { case (t, es) =>
        object CmpEvent extends Ordering[String] {
          def compare(x: String, y: String): Int = {
            def score(et: String) = {
              if (et == "lap") 1
              else if (et.startsWith("split")) 2
              else if (et == "end") -1
              else 0
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

        val eventsRange = events.dropWhile(_.stamp <= begTime).takeWhile(_.stamp < endTime)

        val distRange = dist.pickData(dist.slice(begTime, endTime).stream)
        val gpsRange = gps.pickData(gps.slice(begTime, endTime).stream)

        val attrRange = attributes.map { attr =>
          attr.slice(begTime, endTime)
        }

        val act = ActivityEvents(id.copy(startTime = begTime), eventsRange, distRange, gpsRange, attrRange)

        act
      }
    }

    def span(time: ZonedDateTime): (Option[ActivityEvents], Option[ActivityEvents]) = {

      val (takeDist, leftDist) = dist.span(time)
      val (takeGps, leftGps) = gps.span(time)
      val splitAttributes = attributes.map(_.span(time))

      val takeAttributes = splitAttributes.map(_._1)
      val leftAttributes = splitAttributes.map(_._2)

      val (takeEvents, leftEvents) = events.span(_.stamp < time)

      val (takeBegTime, takeEndTime) = (startTime, time)

      val (leftBegTime, leftEndTime) = (time, endTime)

      val takeMove = if (takeBegTime < takeEndTime) {
        Some(ActivityEvents(id.copy(startTime = takeBegTime, endTime = takeEndTime), takeEvents, takeDist, takeGps, takeAttributes))
      } else None
      val leftMove = if (leftBegTime < leftEndTime) {
        Some(ActivityEvents(id.copy(startTime = leftBegTime, endTime = leftEndTime), leftEvents, leftDist, leftGps, leftAttributes))
      } else None

      (takeMove, leftMove)
    }

    def timeOffset(offset: Int): ActivityEvents = {
      copy(
        id = id.timeOffset(offset),
        events = events.map(_.timeOffset(offset)),
        gps = gps.timeOffset(offset),
        dist = dist.timeOffset(offset),
        attributes = attributes.map(_.timeOffset(offset)))
    }

  }

  trait ActivityStreams {
    def dist: DataStreamDist

    def latlng: DataStreamGPS

    def attributes: Seq[DataStream]
  }

  def detectSportBySpeed(speedStats: (Double, Double, Double), defaultName: Event.Sport) = {
    val (avg, fast, max) = speedStats
    def detectSport(maxRun: Double, fastRun: Double, avgRun: Double): Event.Sport = {
      if (avg <= avgRun && fast <= fastRun && max <= maxRun) Event.Sport.Run
      else Event.Sport.Ride
    }

    def paceToKmh(pace: Double) = 60 / pace

    def kmh(speed: Double) = speed

    val sport = defaultName match {
      case Event.Sport.Run =>
        // marked as run, however if clearly contradicting evidence is found, make it ride
        detectSport(paceToKmh(2), paceToKmh(2.5), paceToKmh(3)) // 2 - 3 min/km possible
      case Event.Sport.Ride =>
        detectSport(kmh(20), kmh(17), kmh(15)) // 25 - 18 km/h possible
      case Event.Sport.Workout =>
        detectSport(paceToKmh(3), paceToKmh(4), paceToKmh(4)) // 3 - 4 min/km possible
      case s => s
    }
    sport
  }

  def processActivityStream(actId: ActivityId, act: ActivityStreams, laps: Seq[ZonedDateTime], segments: Seq[Event]): ActivityEvents = {

    val cleanLaps = laps.filter(l => l > actId.startTime && l < actId.endTime)

    val distStream = if (act.latlng.stream.nonEmpty) {
      act.latlng.distStream
    } else {
      DataStreamGPS.distStreamFromRouteStream(act.dist.stream)
    }

    val smoothingSec = 10
    val speedStream = DataStreamGPS.computeSpeedStream(distStream, smoothingSec)
    val speedMap = speedStream

    // integrate route distance back from smoothed speed stream so that we are processing consistent data
    val routeDistance = DataStreamGPS.routeStreamFromSpeedStream(speedStream)

    // find pause candidates: times when smoothed speed is very low
    val speedPauseMax = 0.7
    val speedPauseAvg = 0.2

    // select samples which are slow and the following is also slow (can be in the middle of the pause)
    type PauseStream = Seq[(ZonedDateTime, ZonedDateTime, Double)]
    val pauseSpeeds: PauseStream = (speedStream zip speedStream.drop(1)).collect {
      case ((t1, s1), (t2, s2)) if s1 < speedPauseMax && s2< speedPauseMax => (t1, t2, s1)
    }.toSeq
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

    def intervalTooShort(beg: ZonedDateTime, end: ZonedDateTime) = {
      val duration = Seconds.secondsBetween(beg, end).getSeconds
      val distance = avgSpeedDuring(beg, end) * duration
      duration < 60 && distance < 100
    }

    val intervals = intervalTimes zip intervalTimes.drop(1)

    val sportsInRanges = intervals.flatMap { case (pBeg, pEnd) =>

      assert(pEnd > pBeg)
      if (sportChangePauses.exists(_._1 == pBeg) || intervalTooShort(pBeg, pEnd)) {
        None // no sport detection during pauses (would always detect as something slow, like Run
      } else {

        val spd = speedDuringInterval(pBeg, pEnd)

        val speedStats = DataStreamGPS.speedStats(spd)

        val sport = detectSportBySpeed(speedStats, actId.sportName)

        Some(pBeg, sport)
      }
    }

    // reversed, as we will be searching for last lower than
    val sportsByTime = sportsInRanges.sortBy(_._1)(Ordering[ZonedDateTime].reverse)

    def findSport(time: ZonedDateTime) = {
      sportsByTime.find(_._1 <= time).map(_._2).getOrElse(actId.sportName)
    }

    val events = (BegEvent(actId.startTime, findSport(actId.startTime)) +: EndEvent(actId.endTime) +: cleanLaps.map(LapEvent)) ++ segments ++ pauseEvents
    val eventsByTime = events.sortBy(_.stamp)

    val sports = eventsByTime.map(x => findSport(x.stamp))

    // insert / modify splits on edges
    val sportChange = (("" +: sports) zip sports).map(ab => ab._1 != ab._2)
    val ees = (eventsByTime, sports, sportChange).zipped.map { case (e1, sport,change) =>
      // TODO: handle multiple events at the same time
      if (change) SplitEvent(e1.stamp, sport)
      else e1
    }

    ActivityEvents(actId, ees.toArray, act.dist, act.latlng, act.attributes)
  }

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    println(s"Download from strava $id")
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

      val dist = new DataStreamDist(SortedMap(timeValues zip distValues:_*))
      val latlng = new DataStreamGPS(SortedMap(timeValues zip latLngAltValues:_*))
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
      e match {
        case ev@EndEvent(_) => Some(ev)
        case _ =>
          if (ei.startsWith("split")) {
            val sportName = ei.substring("split".length)
            Some(SplitEvent(e.stamp, Event.Sport.withName(sportName)))
          } else ei match {
            case "lap" => Some(LapEvent(e.stamp))
            case "end" => Some(EndEvent(e.stamp))
            case _ => None
          }
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

    val periodToFormat = new Period(duration*1000, PeriodType.dayTime)
    myFormat.print(periodToFormat)
  }

  def displayDistance(dist: Double): String = "%.2f".format(dist*0.001)

  def displayDate(startTime: ZonedDateTime): String = {
    ISODateTimeFormat.dateTime().print(startTime)
  }

  def jsDateRange(startTime: ZonedDateTime, endTime: ZonedDateTime): String = {
    s"""formatDateTime("$startTime") + "..." + formatTime("$endTime") """
  }

  def localeDateRange(startTime: ZonedDateTime, endTime: ZonedDateTime): String = {
    // TODO: get timezone and locale from the browser
    val locale = new Locale("cs")

    val zone = startTime.getZone

    val formatDT = DateTimeFormat.forStyle("MS").withLocale(locale).withZone(zone)
    val formatT = DateTimeFormat.forStyle("-S").withLocale(locale).withZone(zone)
    (if (endTime.getMillis - startTime.getMillis < 24 * 3600 * 1000) {
      formatDT.print(startTime) + ".." + formatT.print(endTime)
    } else {
      formatDT.print(startTime) + ".." + formatDT.print(endTime)
    }) + " " + zone.toString
  }
}





