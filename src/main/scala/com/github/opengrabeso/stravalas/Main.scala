package com.github.opengrabeso.stravalas

import java.util
import java.util.logging.Logger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.google.api.client.http.{GenericUrl, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson.JacksonFactory
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.joda.time.{DateTime, Period, Seconds}

import scala.collection.JavaConverters._
import org.joda.time.format.PeriodFormatterBuilder

object Main {
  private val transport = new NetHttpTransport()
  private val jsonFactory = new JacksonFactory()
  private val jsonMapper = new ObjectMapper()

  private val logger = Logger.getLogger(Main.getClass.getName)

  private val requestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
    override def initialize(request: HttpRequest) = request.setParser(new JsonObjectParser(jsonFactory))
  })

  def buildGetRequest(uri: String, authToken: String, parameters: String): HttpRequest = {
    val request = requestFactory.buildGetRequest(new GenericUrl(uri + "?access_token=" + authToken + "&" + parameters))
    request
  }

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

  case class StravaAuthResult(token: String, mapboxToken: String)

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

    StravaAuthResult(token, mapboxToken)

  }

  def authorizeHeaders(request: HttpRequest, authToken: String) = {
    val headers = request.getHeaders
    headers.put("Authorization:", s"Bearer $authToken")
  }

  def athlete(authToken: String): String = {
    val uri = s"https://www.strava.com/api/v3/athlete"
    val request = buildGetRequest(uri, authToken, "")

    val response = request.execute().getContent

    val json = jsonMapper.readTree(response)

    val firstname = json.path("firstname").textValue
    val lastname = json.path("lastname").textValue
    firstname + " " + lastname
  }

  case class ActivityId(
    id: Long, name: String, startTime: DateTime, sportName: String, duration:Int, distance: Double,
    begLat: Double, begLon: Double, endLat: Double, endLon: Double
  ) {
    def link: String = s"https://www.strava.com/activities/$id"
    def lat: Double = (begLat + endLat) * 0.5
    def lon: Double = (begLon + endLon) * 0.5
  }

  object ActivityId {
    def load(json: JsonNode): ActivityId = {
      // https://strava.github.io/api/v3/activities/
      val name = json.path("name").textValue
      val id = json.path("id").longValue
      val time = DateTime.parse(json.path("start_date").textValue)
      val sportName = json.path("type").textValue
      val duration = json.path("elapsed_time").intValue
      val distance = json.path("distance").doubleValue
      val begLat  = json.path("start_latlng").path(0).doubleValue
      val begLon  = json.path("start_latlng").path(1).doubleValue
      val endLat  = json.path("end_latlng").path(0).doubleValue
      val endLon  = json.path("end_latlng").path(1).doubleValue

      ActivityId(id, name, time, sportName, duration, distance, begLat, begLon, endLat, endLon)
    }
  }

  def lastActivities(authToken: String): Array[ActivityId] = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, authToken, "per_page=15")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    (0 until responseJson.size).map(i => ActivityId.load(responseJson.get(i)))(collection.breakOut)
  }

  case class ActivityEvents(id: ActivityId, events: Array[Event], sports: Array[String], stamps: Seq[Stamp], gps: Seq[(Double, Double)], attributes: Seq[(String, Seq[Int])]) {
    def routeJS: String = {
      (gps zip stamps).map { case ((lng, lat),t) =>
        s"[$lat,$lng,${t.time},${t.dist}]"
      }.mkString("[\n", ",\n", "]\n")
    }

    def editableEvents: Array[EditableEvent] = {

      def neq(a: String, b: String) = a != b
      val sportChange = (("" +: sports) zip sports).map((neq _). tupled)

      val ees = (events, events.drop(1) :+ events.last, sports zip sportChange).zipped.map { case (e1, e2, (sport,change)) =>
        val action = if (change) "split" else e1.defaultEvent
        EditableEvent(action, e1, sport)
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

      val splitTimes = splitEvents.map(_.stamp.time)

      assert(splitTimes.contains(0))
      assert(splitTimes.contains(stamps.last.time))

      val splitRanges = splitEvents zip splitTimes.tail

      val toSplit = splitRanges.find(_._1.stamp.time == splitTime)

      toSplit.map { case (beg, endTime) =>
        val begTime = beg.stamp.time


        val eventsRange = (events zip sports).dropWhile(_._1.stamp.time <= begTime).takeWhile(_._1.stamp.time < endTime)

        val indexBeg = stamps.map(_.time).lastIndexWhere(_ <= begTime) max 0

        def safeIndexWhere[T](seq: Seq[T])(pred: T => Boolean) = {
          val i = seq.indexWhere(pred)
          if (i < 0) seq.size else i
        }
        val indexEnd = safeIndexWhere(stamps)(_.time > endTime) min stamps.size

        val stampsRange = stamps.slice(indexBeg, indexEnd)
        val gpsRange = gps.slice(indexBeg, indexEnd)

        val attrRange = attributes.map { case (name, attr) =>
          (name, attr.slice(indexBeg, indexEnd))
        }

        val actTime = id.startTime.plusSeconds(begTime)

        val act = ActivityEvents(id.copy(startTime = actTime), eventsRange.map(_._1), eventsRange.map(_._2), stampsRange, gpsRange, attrRange)

        act
      }
    }
  }

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = ActivityId.load(responseJson)
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

    val laps = {

      val requestLaps = buildGetRequest(s"https://www.strava.com/api/v3/activities/$id/laps", authToken, "")

      val response = requestLaps.execute().getContent

      val lapsJson = jsonMapper.readTree(response)

      val lapTimes = (for (lap <- lapsJson.elements.asScala) yield {
        val lapTimeStr = lap.path("start_date").textValue
        DateTime.parse(lapTimeStr)
      }).toList


      val lapsInSeconds = lapTimes.map(lap => Seconds.secondsBetween(startTime, lap).getSeconds)

      lapsInSeconds.filter(_ > 0).map(ActivityStreams.stampForTime)
    }

    val segments: Seq[Event] = {
      val segmentList = responseJson.path("segment_efforts").asScala.toList
      segmentList.flatMap {seg =>
        val segStartTime = DateTime.parse(seg.path("start_date").textValue)
        val segName = seg.path("name").textValue
        val segStart = Seconds.secondsBetween(startTime, segStartTime).getSeconds
        val segDuration = seg.path("elapsed_time").intValue
        val segPrivate = seg.path("segment").path("private").booleanValue
        Seq(
          StartSegEvent(segName, segPrivate, ActivityStreams.stampForTime(segStart)),
          EndSegEvent(segName, segPrivate, ActivityStreams.stampForTime(segStart + segDuration))
        )
      }
    }

    val pauses: Seq[(Int, Stamp)] = {

      // compute speed from a distance stream
      val maxPauseSpeed = 0.2
      val maxPauseSpeedImmediate = 0.7
      def pauseDuration(path: List[Double], time: List[Int]): Int = {
        def pauseDurationRecurse(start: Double, prev: Double, duration: Int, path: List[Double], time: List[Int]): Int = {
          path match {
            case head +: next +: tail if head - start <= maxPauseSpeed * duration && head - prev <= maxPauseSpeedImmediate * (time.tail.head - time.head) =>
              pauseDurationRecurse(start, head, duration + time.tail.head - time.head, path.tail, time.tail)
            case _ => duration
          }
        }
        pauseDurationRecurse(path.head, path.head, 0, path, time)
      }

      def computePauses(dist: List[Double], time: List[Int], pauses: List[Int]): List[Int] = {
        dist match {
          case head +: tail =>
            computePauses(tail, time.tail, pauseDuration(dist, time) +: pauses)
          case _ => pauses
        }
      }

      // ignore following too close
      def ignoreTooClose(pause: Int, pauses: List[Int], ret: List[Int]): List[Int] = {
        pauses match {
          case head +: tail =>
            if (head < pause) ignoreTooClose(pause - 1, tail, 1 +: ret)
            else ignoreTooClose(head, tail, head +: ret)
          case _ => ret
        }
      }

      import ActivityStreams._
      val pauses = computePauses(dist.toList, time.toList, Nil).reverse // reverse to keep concat fast

      val cleanedPauses = ignoreTooClose(0, pauses, Nil).reverse

      val minPause = 10
      val longPauses = (cleanedPauses.zipWithIndex zip stamps).filter { case ((p,i), stamp) =>
        p > minPause
      }.map {
        case ((p,i), stamp) => (p, stamp)
      }

      longPauses
    }

    def mergePauses(pauses: Seq[(Int, Stamp)], ret: Seq[(Int, Stamp)]): Seq[(Int, Stamp)] = {
      def merge(head: (Int, Stamp), next: (Int, Stamp)) = {
        (next._2.time + next._1 - head._2.time, head._2)
      }

      pauses match {
        case head +: next +: tail =>
          val ignoreShortMovement = 15
          if (next._2.time - head._2.time - head._1 < ignoreShortMovement) mergePauses(merge(head, next) +: tail, ret)
          else mergePauses(next +: tail, head +: ret)
        case _ => pauses ++ ret
      }
    }

    val mergedPauses = mergePauses(pauses.sortBy(_._2.time), Seq() )

    val pauseEvents = mergedPauses.flatMap { case (p, stamp) =>
      if (p > 30) {
        // long pause - insert both start and end
        Seq(PauseEvent(p, stamp), PauseEndEvent(p, stamp.offset(p, 0)))
      }
      else Seq(PauseEvent(p, stamp))
    }

    val pauseTimes = (0 +: pauses.map(_._2.time) :+ ActivityStreams.time.last).distinct

    val pauseRanges = pauseTimes zip pauseTimes.drop(1)

    class SpeedStats(begTime: Int, endTime: Int) {
      import ActivityStreams._

      val beg = time.indexWhere(_ >= begTime)
      val end = time.indexWhere(_ >= endTime)

      def statDistance = dist(end-1) - dist(beg)
      def statDuration = time(end-1) - time(beg)

      class SlidingAverage(wantedDuration: Int) {
        class Window(begIndex: Int, endIndex: Int) {
          def distance = dist(endIndex-1) - dist(begIndex)
          def duration = time(endIndex-1) - time(begIndex)

          def speed: Double = if (duration > 0) distance / duration else 0

          def advance: Option[Window] = {
            if (endIndex >= end) None
            else {
              val newEnd = endIndex + 1
              if (newEnd - begIndex > wantedDuration) {
                val newBeg = begIndex + 1
                Some(new Window(newBeg, newEnd))
              } else {
                new Window(begIndex, newEnd).advance
              }
            }
          }
        }

        var slide = new Window(beg, beg).advance
        val maxSpeed = {
          var maxSpeed = 0.0
          while (slide.isDefined) {
            val v = slide.get.speed
            maxSpeed = maxSpeed max v
            slide = slide.get.advance
          }
          maxSpeed
        }

      }

      val max30 = new SlidingAverage(30).maxSpeed
      val max120 = new SlidingAverage(120).maxSpeed
      val max600 = new SlidingAverage(600).maxSpeed

    }

    val sportsInRanges = for ((pBeg, pEnd) <- pauseRanges) yield {
      val stats = new SpeedStats(pBeg, pEnd)

      def paceToMs(pace: Double) = 60 / pace / 3.6
      def kmhToMs(speed: Double) = speed / 3.6

      def detectSport(maxRunSpeed30: Double, maxRunSpeed120: Double, maxRunSpeed600: Double): String = {
        if (stats.max30 <= maxRunSpeed30 && stats.max120 <= maxRunSpeed120) "Run"
        else "Ride"
      }

      val sport = actId.sportName.toLowerCase match {
        case "run" =>
          // marked as run, however if clearly contradicting evidence is found, make it ride
          detectSport(paceToMs(2), paceToMs(3), paceToMs(3)) // 2 - 3 min/km possible
        case "ride" =>
          detectSport(kmhToMs(25), kmhToMs(22), kmhToMs(20)) // 25 - 18 km/h possible
          //if (stats.statDuration > 10)
        case _ =>
          detectSport(paceToMs(3), paceToMs(4), paceToMs(4)) // 3 - 4 min/km possible
        // TODO: handle other sports: swimming, walking, ....
      }

      (pBeg, sport)
    }

    val sportsByTime = sportsInRanges.sortBy(-_._1)
    def findSport(time: Int) = {
      sportsByTime.find(_._1 <= time).map(_._2).getOrElse(actId.sportName)
    }
    import ActivityStreams._
    // TODO: provide activity type with the split
    val events = (BegEvent(Stamp(0,0)) +: EndEvent(Stamp(time.last, dist.last)) +: laps.map(LapEvent)) ++ pauseEvents ++ segments

    val eventsByTime = events.sortBy(_.stamp.time)

    val sports = eventsByTime.map(x => findSport(x.stamp.time))


    ActivityEvents(actId, eventsByTime.toArray, sports.toArray, stamps, latlng, Seq(heartrate, cadence, watts, temp))
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

class Download extends HttpServlet {

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {

    val id = req.getParameter("id")
    val op = req.getParameter("operation")
    val authToken = req.getParameter("auth_token")

    val contentType = "application/octet-stream"

    def download(export: Array[Byte], filename: String): Unit = {
      resp.setContentType(contentType)
      resp.setStatus(200)
      resp.setHeader("Content-Disposition", filename)

      val out = resp.getOutputStream
      out.write(export)
    }

    op match {
      case "split" =>
        val eventsInput = req.getParameterValues("events")
        val splitTime = req.getParameter("time").toInt
        val session = req.getSession

        val events = session.getAttribute("events-" + id).asInstanceOf[Main.ActivityEvents]

        val adjusted = Main.adjustEvents(events, eventsInput)

        val split = adjusted.split(splitTime)

        split.foreach{ save =>

          val export = FitExport.export(save)

          download(export, s"attachment;filename=split_${id}_$splitTime.fit")
        }

      case "process" =>

        val eventsInput = req.getParameterValues("events")

        val events = Main.getEventsFrom(authToken, id)

        val adjusted = Main.adjustEvents(events, eventsInput)

        val export = FitExport.export(adjusted)

        download(export, s"attachment;filename=split_$id.fit")

      case "copy" =>
        val exportUri = s"https://www.strava.com/activities/$id/export_tcx"
        /*
        val dispatcher = req.getRequestDispatcher(exportUri)
        dispatcher.forward(req, resp)
        */
        resp.sendRedirect(exportUri)

    }
  }
}

class RouteData extends HttpServlet {

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {

    val id = req.getParameter("id")
    val authToken = req.getParameter("auth_token")

    val contentType = "application/json"

    val session = req.getSession

    val events = session.getAttribute("events-"+id).asInstanceOf[Main.ActivityEvents]

    resp.setContentType(contentType)
    resp.setStatus(200)

    val out = resp.getWriter
    out.write(events.routeJS)
  }
}
