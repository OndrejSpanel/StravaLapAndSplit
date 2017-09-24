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
import com.github.opengrabeso.stravamat.DataStreamGPS.SpeedStats
import com.github.opengrabeso.stravamat.shared.Timing
import com.google.api.client.json.jackson2.JacksonFactory

import scala.annotation.tailrec
import scala.collection.immutable.{SortedMap, SortedSet}
import scala.util.Try
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

      def sportFromName(name: String): Event.Sport = {
        try {
          Event.Sport.withName(sportName)
        } catch {
          case _: NoSuchElementException => Event.Sport.Workout
        }
      }

      ActivityId(StravaId(id), actDigest, name, time, time.plusSeconds(duration), sportFromName(sportName), distance)
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
    // stage are data visible to the user
    val edit = "edit"
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
        Storage.load[ActivityHeader](namespace.stage, a, auth.userId)
      }
    }
    storedActivities.toVector
  }

  @SerialVersionUID(10L)
  case object NoActivity

  @SerialVersionUID(11L)
  case class ActivityHeader(id: ActivityId, hasGPS: Boolean, hasAttributes: Boolean, stats: SpeedStats) {
    override def toString = id.toString
  }

  object ActivityEvents {
    def mergeAttributes(thisAttributes: Seq[DataStream], thatAttributes: Seq[DataStream]): Seq[DataStream] = {
      val mergedAttr = thisAttributes.map { a =>
        val aThat = thatAttributes.find(_.streamType == a.streamType)
        val aStream = aThat.map(a.stream ++ _.stream).getOrElse(a.stream)
        a.pickData(aStream.asInstanceOf[a.DataMap])
      }
      val notMergedFromThat = thatAttributes.find(ta => !thisAttributes.exists(_.streamType == ta.streamType))
      mergedAttr ++ notMergedFromThat
    }
  }

  @SerialVersionUID(10L)
  case class ActivityEvents(id: ActivityId, events: Array[Event], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream]) {
    import ActivityEvents._

    def computeDistStream = {
      if (gps.stream.nonEmpty) {
        gps.distStream
      } else {
        DataStreamGPS.distStreamFromRouteStream(dist.stream.toSeq)
      }
    }

    def computeSpeedStats: SpeedStats = DataStreamGPS.speedStats(DataStreamGPS.computeSpeedStream(computeDistStream))

    def header: ActivityHeader = ActivityHeader(id, hasGPS, hasAttributes, computeSpeedStats)

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


    def eventTimes: DataStream.EventTimes = SortedSet(events.map(_.stamp):_*)
    def optimize: ActivityEvents = {
      // first optimize all attributes
      val times = eventTimes
      this.copy(gps = gps.optimize(times), dist = dist.optimize(times), attributes = attributes.map(_.optimize(times)))
    }

    def optimizeRouteForMap: Seq[(ZonedDateTime, GPSPoint)] = {
      val maxPoints = 3000
      if (gps.stream.size < maxPoints) gps.stream.toList
      else {
        // first apply generic GPS optimization
        val data = gps.optimize(eventTimes)

        if (data.stream.size < maxPoints) data.stream.toList
        else {
          val ratio = (data.stream.size / maxPoints.toDouble).ceil.toInt
          val gpsSeq = data.stream.toList

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
    }

    def routeJS: String = {
      val toSend = optimizeRouteForMap

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

      // when activities follow each other, insert a lap or a pause between them
      val lastBeg = begs.map(_.stamp).max
      val firstEnd = ends.map(_.stamp).min

      val transitionEvents = if (firstEnd <= lastBeg) {
        val duration = timeDifference(firstEnd, lastBeg).toInt
        if (duration < 60) {
          Seq(LapEvent(firstEnd), LapEvent(lastBeg))
        } else {
          Seq(PauseEvent(duration, firstEnd), PauseEndEvent(duration, lastBeg))
        }
      } else {
        Nil
      }

      val eventsAndSportsSorted = (begsAdjusted ++ rest ++ transitionEvents :+ ends.maxBy(_.stamp) ).sortBy(_.stamp)

      val startBegTimes = Seq(this.startTime, this.endTime, that.startTime, that.endTime).sorted

      val timeIntervals = startBegTimes zip startBegTimes.tail

      val streams = for (timeRange <- timeIntervals) yield {
        // do not merge overlapping distances, prefer distance from a GPS source
        val thisGpsPart = this.gps.slice(timeRange._1, timeRange._2)
        val thatGpsPart = that.gps.slice(timeRange._1, timeRange._2)

        val thisDistPart = this.dist.slice(timeRange._1, timeRange._2)
        val thatDistPart = that.dist.slice(timeRange._1, timeRange._2)

        val thisAttrPart = this.attributes.map(_.slice(timeRange._1, timeRange._2))
        val thatAttrPart = that.attributes.map(_.slice(timeRange._1, timeRange._2))

        (
          if (thisGpsPart.stream.size > thatGpsPart.stream.size) thisGpsPart else thatGpsPart,
          if (thisDistPart.stream.size > thatDistPart.stream.size) thisDistPart else thatDistPart,
          // assume we can use attributes from both sources, do not prefer one over another
          mergeAttributes(thisAttrPart, thatAttrPart)
        )
      }

      // distance streams need offsetting
      // when some part missing a distance stream, we need to compute the offset from GPS

      var offset = 0.0
      val offsetStreams = for ((gps, dist, attr) <- streams) yield {
        val partDist = dist.stream.lastOption.fold(gps.distStream.lastOption.fold(0.0)(_._2))(_._2)
        val startOffset = offset
        offset += partDist
        (gps.stream, dist.offsetDist(startOffset).stream, attr)
      }

      val totals = offsetStreams.fold(offsetStreams.head) { case ((totGps, totDist, totAttr), (iGps, iDist, iAttr)) =>
        (totGps ++ iGps, totDist ++ iDist, mergeAttributes(totAttr, iAttr))
      }

      ActivityEvents(mergedId, eventsAndSportsSorted, dist.pickData(totals._2), gps.pickData(totals._1), totals._3)
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

        val sport = beg.sportChange.getOrElse(id.sportName)

        val act = ActivityEvents(id.copy(startTime = begTime, sportName = sport), eventsRange, distRange, gpsRange, attrRange)

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

    def processPausesAndEvents: ActivityEvents = {
      implicit val start = Timing.Start()
      //val cleanLaps = laps.filter(l => l > actId.startTime && l < actId.endTime)

      // prefer GPS, as this is already cleaned for accuracy error
      val distStream = if (this.gps.isEmpty) {
        DataStreamGPS.distStreamFromRouteStream(this.dist.stream.toSeq)
      } else {
        this.gps.distStream
      }

      Timing.logTime("distStream")

      val speedStream = DataStreamGPS.computeSpeedStream(distStream)
      val speedMap = speedStream

      // integrate route distance back from smoothed speed stream so that we are processing consistent data
      val routeDistance = DataStreamGPS.routeStreamFromSpeedStream(speedStream)

      Timing.logTime("routeDistance")

      // find pause candidates: times when smoothed speed is very low
      val speedPauseMax = 0.7
      val speedPauseAvg = 0.4
      val minPause = 10 // minimal pause to record
      val minLongPause = 20 // minimal pause to introduce end pause event
      val minSportChangePause = 50  // minimal pause to introduce automatic transition between sports
      val minSportDuration = 15 * 60 // do not change sport too often, assume at least 15 minutes of activity

      // select samples which are slow and the following is also slow (can be in the middle of the pause)
      type PauseStream = List[(ZonedDateTime, ZonedDateTime, Double)]
      val pauseSpeeds: PauseStream = (speedStream zip speedStream.drop(1)).collect {
        case ((t1, s1), (t2, s2)) if s1 < speedPauseMax && s2< speedPauseMax => (t1, t2, s1)
      }.toList
      // aggregate pause intervals - merge all
      def mergePauses(pauses: PauseStream, done: PauseStream): PauseStream = {
        pauses match {
          case head :: next :: tail =>
            if (head._2 == next._1) { // extend head with next and repeat
              mergePauses(head.copy(_2 = next._2) :: tail, done)
            } else { // head can no longer be extended, use it, continue processing
              mergePauses(next +: tail, head +: done)
            }
          case _ => pauses ++ done
        }
      }

      val mergedPauses = mergePauses(pauseSpeeds, Nil).reverse

      Timing.logTime("mergePauses")

      def avgSpeedDuring(beg: ZonedDateTime, end: ZonedDateTime): Double = {
        val findBeg = routeDistance.to(beg).lastOption
        val findEnd = routeDistance.from(end).headOption
        val avgSpeed = for (b <- findBeg; e <- findEnd) yield {
          val duration = Seconds.secondsBetween(b._1, e._1).getSeconds
          if (duration > 0) (e._2 - b._2) / duration else 0
        }
        avgSpeed.getOrElse(0)
      }

      type Pause = (ZonedDateTime, ZonedDateTime)
      def pauseDuration(p: Pause) = timeDifference(p._1, p._2)

      // take a pause candidate and reduce its size until we get a real pause (or nothing)
      def extractPause(beg: ZonedDateTime, end: ZonedDateTime): List[Pause] = {

        val pauseArea = speedStream.from(beg).to(end)

        // locate a point which is under required avg speed, this is guaranteed to serve as a possible pause center
        val (_, candidateStart) = pauseArea.span(_._2 > speedPauseAvg)
        val (candidate, left) = candidateStart.span(_._2 <= speedPauseAvg)
        // now take all under the speed

        def isPauseDuring(b: ZonedDateTime, e: ZonedDateTime, rect: DataStreamGPS.GPSRect) = {
          val gpsRange = gps.stream.from(b).to(e)

          val extendRect = for {
            gpsBeg <- gpsRange.headOption
            gpsEnd <- gpsRange.lastOption
          } yield {
            rect.merge(gpsBeg._2).merge(gpsEnd._2)
          }
          val extendedRect = extendRect.getOrElse(rect)
          val rectSize = extendedRect.size
          val rectDuration = Seconds.secondsBetween(b, e).getSeconds
          val rectSpeed = if (rectDuration > 0) rectSize / rectDuration else 0
          // until the pause is long enough, do not evaluate its speed
          (rectSpeed < speedPauseAvg || rectDuration < minPause, extendedRect)
        }

        def extendPause(b: ZonedDateTime, e: ZonedDateTime, rect: DataStreamGPS.GPSRect): Pause = {
          // try extending beg first
          // b .. e is inclusive
          val prevB = pauseArea.to(b).dropRight(1).lastOption.map(_._1)
          val nextE = pauseArea.from(e).drop(1).headOption.map(_._1)

          val pauseB = prevB.map(isPauseDuring(_, e, rect))
          val pauseE = nextE.map(isPauseDuring(b, _, rect))
          if (pauseB.isDefined && pauseB.get._1) {
            extendPause(prevB.get, e, pauseB.get._2)
          } else if (pauseE.isDefined && pauseE.get._1) {
            extendPause(b, nextE.get, pauseE.get._2)
          } else {
            (beg, end)
          }
        }

        val candidateRange = for {
          b <- candidate.headOption
          e <- candidate.lastOption
        } yield {
          (b._1, e._1)
        }

        val candidatePause = candidateRange.toList.flatMap { case (cb, ce) =>
          val gpsRange = gps.stream.from(cb).to(ce)
          val gpsRect = gpsRange.foldLeft(new DataStreamGPS.GPSRect(gpsRange.head._2))((rect, p) => rect merge p._2)
          val cp = extendPause(cb, ce, gpsRect)
          // skip the extended pause
          val next = pauseArea.from(cp._2).drop(1).headOption
          next.map(n => cp :: extractPause(n._1, end)).getOrElse(List(cp))
        }
        candidatePause
      }

      def cleanPauses(ps: List[Pause]): List[Pause] = {
        // when pauses are too close to each other, delete them or merge them
        def recurse(todo: List[Pause], done: List[Pause]): List[Pause] = {
          def shouldBeMerged(first: (ZonedDateTime, ZonedDateTime), second: (ZonedDateTime, ZonedDateTime)) = {
            timeDifference(first._2, second._1) < 120 && avgSpeedDuring(first._2, second._1) < 2
          }

          def shouldBeDiscardedFirst(first: (ZonedDateTime, ZonedDateTime), second: (ZonedDateTime, ZonedDateTime)) = {
            timeDifference(first._2, second._1) < 240
          }

          todo match {
            case first :: second :: tail if shouldBeMerged(first, second) =>
              recurse((first._1, second._2) :: tail, done)
            case first :: second :: tail if shouldBeDiscardedFirst(first, second) =>
              val longer = Seq(first, second).maxBy(pauseDuration)
              recurse(longer :: tail, done)
            case head :: tail =>
              recurse(tail, head :: done)
            case _ =>
              done
          }
        }
        recurse(ps, Nil).reverse
      }

      val extractedPauses = mergedPauses.flatMap(p => extractPause(p._1, p._2))

      Timing.logTime("extractedPauses")

      val cleanedPauses = cleanPauses(extractedPauses)

      val pauseEvents = cleanedPauses.flatMap { case (tBeg, tEnd) =>
        val duration = Seconds.secondsBetween(tBeg, tEnd).getSeconds
        if (duration > minLongPause) {
          Seq(PauseEvent(duration, tBeg), PauseEndEvent(duration, tEnd))
        } else if (duration > minPause) {
          Seq(PauseEvent(duration, tBeg))
        } else Seq()
      }

      def collectSportChanges(todo: List[Pause], done: List[Pause]): List[Pause] = {
        todo match {
          case first :: second :: tail if timeDifference(first._2, second._1) < minSportDuration =>
            val longer = Seq(first, second).maxBy(pauseDuration)
            collectSportChanges(longer :: tail, done)
          case head :: tail if timeDifference(head._1, head._2) > minSportChangePause =>
            collectSportChanges(tail, head :: done)
          case head :: tail =>
            collectSportChanges(tail, done)
          case _ =>
            done
        }
      }

      val sportChangePauses = collectSportChanges(cleanedPauses, Nil).reverse

      val sportChangeTimes = sportChangePauses.flatMap(p => Seq(p._1, p._2))

      val intervalTimes = (id.startTime +: sportChangeTimes :+ id.endTime).distinct

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

          val sport = detectSportBySpeed(speedStats, id.sportName)

          Some(pBeg, sport)
        }
      }

      // reversed, as we will be searching for last lower than
      val sportsByTime = sportsInRanges.sortBy(_._1)(Ordering[ZonedDateTime].reverse)

      def findSport(time: ZonedDateTime) = {
        sportsByTime.find(_._1 <= time).map(_._2).getOrElse(id.sportName)
      }

      // process existing events
      val inheritEvents = this.events.filterNot(_.isSplit)

      val events = (BegEvent(id.startTime, findSport(id.startTime)) +: EndEvent(id.endTime) +: inheritEvents) ++ pauseEvents
      val eventsByTime = events.sortBy(_.stamp)

      val sports = eventsByTime.map(x => findSport(x.stamp))

      // insert / modify splits on edges
      val sportChange = (("" +: sports) zip sports).map(ab => ab._1 != ab._2)
      val allEvents = (eventsByTime, sports, sportChange).zipped.map { case (ev, sport,change) =>
        // TODO: handle multiple events at the same time
        if (change) {
          if (ev.isInstanceOf[BegEvent]) BegEvent(ev.stamp, sport)
          else SplitEvent(ev.stamp, sport)
        }
        else ev
      }

      // when there are multiple events at the same time, use only the most important one
      @tailrec
      def cleanupEvents(es: List[Event], ret: List[Event]): List[Event] = {
        es match {
          case first :: second :: tail if first.stamp == second.stamp =>
            if (first.order < second.order) cleanupEvents(first :: tail, ret)
            else cleanupEvents(second :: tail, ret)
          case head :: tail =>
            cleanupEvents(tail, head :: ret)
          case _ =>
            ret
        }
      }

      val cleanedEvents = cleanupEvents(allEvents.sortBy(_.stamp).toList, Nil).reverse

      Timing.logTime("extractPause done")

      copy(events = cleanedEvents.toArray)
    }


    def cleanPositionErrors: ActivityEvents = {

      def vecFromGPS(g: GPSPoint) = Vector2(g.latitude, g.longitude)
      def gpsFromVec(v: Vector2) = GPSPoint(latitude = v.x, longitude = v.y, None)(None)

      @tailrec
      def cleanGPS(todoGPS: List[gps.ItemWithTime], done: List[gps.ItemWithTime]): List[gps.ItemWithTime] = {
        /* value 1.0 means the sample should be kept, 0.0 means it should be dropped */
        todoGPS match {
          case first :: second :: tail if second._2.accuracy > 8 =>
            // move second as little as possible to stay within GPS accuracy error
            val gps1 = first._2
            val gps2 = second._2
            val v1 = vecFromGPS(gps1)
            val v2 = vecFromGPS(gps2)

            val maxDist = second._2.accuracy * 2 // * 2 is empirical, tested activity looks good with this value
            val dist = gps1 distance gps2
            // move as far from v2 (as close to v1) as accuracy allows
            if (dist > maxDist) {
              val clamped = (v1 - v2) * (maxDist / dist) + v2
              val gpsClamped = gps1.copy(clamped.x, clamped.y)(None)
              cleanGPS(second.copy(_2 = gpsClamped) :: tail, first :: done)
            } else {
              cleanGPS(second.copy(_2 = first._2) :: tail, first :: done)
            }
          case head :: tail =>
            cleanGPS(tail, head :: done)
          case _ =>
            done
        }
      }

      val gpsClean = cleanGPS(gps.stream.toList, Nil).reverse
      val gpsStream = gps.pickData(SortedMap(gpsClean:_*))

      copy(gps = gpsStream)

    }

  }

  trait ActivityStreams {
    def dist: DataStreamDist

    def latlng: DataStreamGPS

    def attributes: Seq[DataStream]
  }

  def detectSportBySpeed(stats: DataStreamGPS.SpeedStats, defaultName: Event.Sport) = {
    def detectSport(maxRun: Double, fastRun: Double, medianRun: Double): Event.Sport = {
      if (stats.median <= medianRun && stats.fast <= fastRun && stats.max <= maxRun) Event.Sport.Run
      else Event.Sport.Ride
    }

    def paceToKmh(pace: Double) = 60 / pace

    def kmh(speed: Double) = speed

    val sport = defaultName match {
      case Event.Sport.Run =>
        // marked as run, however if clearly contradicting evidence is found, make it a ride
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

    val events = (BegEvent(actId.startTime, actId.sportName) +: EndEvent(actId.endTime) +: cleanLaps.map(LapEvent)) ++ segments
    val eventsByTime = events.sortBy(_.stamp)

    ActivityEvents(actId, eventsByTime.toArray, act.dist, act.latlng, act.attributes)
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
      private val wantStreams = Seq("time", "latlng", "distance", "altitude", "heartrate", "cadence", "watts"/*, "temp"*/)

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
        GPSPoint(lat, lng, None)(None)
      }

      val timeRelValues = getDataByName("time", _.asInt)
      val distValues = getDataByName("distance", _.asDouble)
      val latlngValues = getDataByName("latlng", loadGpsPair)
      val altValues = getDataByName("altitude", _.asDouble)

      val latLngAltValues = if (altValues.isEmpty) latlngValues else {
        (latlngValues zip altValues).map { case (gps,alt) =>
            gps.copy(elevation = Some(alt.toInt))(Some(gps.accuracy))
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
      val attributes =  attributeValues.filter(_._2.nonEmpty).flatMap { case (name, values) =>
          name match {
            case "heartrate" => Some(new DataStreamHR(SortedMap(timeValues zip values:_*)))
            case _ => Some(new DataStreamAttrib(name, SortedMap(timeValues zip values:_*)))
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

  def adjustEvents(events: ActivityEvents, eventsInput: Seq[String]): ActivityEvents = {
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





