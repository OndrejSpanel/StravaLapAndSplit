package com.github.opengrabeso.stravalas

import java.util
import java.util.logging.Logger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.google.api.client.http.{GenericUrl, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson.JacksonFactory
import org.joda.time.{DateTime, Period, Seconds}

import scala.collection.JavaConverters._
import com.google.appengine.repackaged.org.codehaus.jackson.JsonNode
import com.google.appengine.repackaged.org.codehaus.jackson.map.ObjectMapper
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

  def secret: (String, String) = {
    val secretStream = Main.getClass.getResourceAsStream("/secret.txt")
    val lines = scala.io.Source.fromInputStream(secretStream).getLines
    (lines.next(), lines.next())
  }

  def stravaAuth(code: String): String = {

    val json = new util.HashMap[String, String]()
    val (clientId, clientSecret) = secret

    json.put("client_id", clientId)
    json.put("client_secret", clientSecret)
    json.put("code", code)

    val content = new JsonHttpContent(new JacksonFactory(), json)

    val request = requestFactory.buildPostRequest(new GenericUrl("https://www.strava.com/oauth/token"), content)
    val response = request.execute() // TODO: async?

    val responseJson = jsonMapper.readTree(response.getContent)
    val token = responseJson.path("access_token").getTextValue

    token

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

    val firstname = json.path("firstname").getTextValue
    val lastname = json.path("lastname").getTextValue
    firstname + " " + lastname
  }

  case class ActivityId(id: Long, name: String, startTime: DateTime, sportName: String, duration:Int, distance: Double) {
    def link: String = s"https://www.strava.com/activities/$id"
  }

  object ActivityId {
    def load(json: JsonNode): ActivityId = {
      // https://strava.github.io/api/v3/activities/
      val name = json.path("name").getTextValue
      val id = json.path("id").getLongValue
      val time = DateTime.parse(json.path("start_date").getTextValue)
      val sportName = json.path("type").getTextValue
      val duration = json.path("elapsed_time").getIntValue
      val distance = json.path("distance").getDoubleValue

      ActivityId(id, name, time, sportName, duration, distance)
    }
  }

  def lastActivities(authToken: String): Array[ActivityId] = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, authToken, "per_page=15")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    (0 until responseJson.size).map(i => ActivityId.load(responseJson.get(i)))(collection.breakOut)
  }

  case class ActivityEvents(id: ActivityId, events: Array[Event], sports: Array[String], time: Seq[Int], gps: Seq[(Double, Double)], attributes: Seq[(String, Seq[Int])]) {
    def editableEvents: Array[EditableEvent] = {

      val ees = (events, events.drop(1) :+ events.last, sports).zipped.map { case (e1, e2, sport) =>
        EditableEvent(e1, sport)
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
      assert(splitTimes.contains(time.last))

      val splitRanges = splitEvents zip splitTimes.tail

      val toSplit = splitRanges.find(_._1.stamp.time == splitTime)

      toSplit.map { case (beg, endTime) =>
        val begTime = beg.stamp.time


        val eventsRange = (events zip sports).dropWhile(_._1.stamp.time <= begTime).takeWhile(_._1.stamp.time < endTime)

        val indexBeg = time.lastIndexWhere(_ <= begTime) max 0

        def safeIndexWhere[T](seq: Seq[T])(pred: T => Boolean) = {
          val i = seq.indexWhere(pred)
          if (i < 0) seq.size else i
        }
        val indexEnd = safeIndexWhere(time)(_ > endTime) min time.size

        val timeRange = time.slice(indexBeg, indexEnd)
        val gpsRange = gps.slice(indexBeg, indexEnd)

        val attrRange = attributes.map { case (name, attr) =>
          (name, attr.slice(indexBeg, indexEnd))
        }

        val actTime = id.startTime.plusSeconds(begTime)

        val act = ActivityEvents(id.copy(startTime = actTime), eventsRange.map(_._1), eventsRange.map(_._2), timeRange, gpsRange, attrRange)

        act
      }
    }
  }

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = ActivityId.load(responseJson)
    val startDateStr = responseJson.path("start_date").getTextValue
    val startTime = DateTime.parse(startDateStr)

    object ActivityStreams {
      //private val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")
      private val wantStreams = Seq("time", "latlng", "distance", "heartrate", "cadence", "watts", "temp")

      private val streamTypes = wantStreams.mkString(",")

      private val uri = s"https://www.strava.com/api/v3/activities/$id/streams/$streamTypes"
      private val request = buildGetRequest(uri, authToken, "")

      private val response = request.execute().getContent

      private val responseJson = jsonMapper.readTree(response)

      val streams = responseJson.getElements.asScala.toIterable

      def getData[T](stream: Stream[JsonNode], get: JsonNode => T): Seq[T] = {
        if (stream.isEmpty) Nil
        else stream.head.path("data").asScala.map(get).toSeq
      }
      def getDataByName[T](name: String, get: JsonNode => T): Seq[T] = {
        val stream = streams.filter(_.path("type").getTextValue == name).toStream
        getData(stream, get)
      }
      def getAttribByName(name: String): (String, Seq[Int]) = {
        name -> getDataByName(name, _.asInt)
      }

      private def loadGpsPair(gpsItem: JsonNode) = {
        val elements = gpsItem.getElements
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

      val lapTimes = (for (lap <- lapsJson.getElements.asScala) yield {
        val lapTimeStr = lap.path("start_date").getTextValue
        DateTime.parse(lapTimeStr)
      }).toSeq


      val lapsInSeconds = lapTimes.map(lap => Seconds.secondsBetween(startTime, lap).getSeconds)

      lapsInSeconds.filter(_ > 0).map(ActivityStreams.stampForTime)
    }

    val segments: Iterable[Event] = {
      val segmentList = responseJson.path("segment_efforts").asScala
      segmentList.flatMap {seg =>
        val segStartTime = DateTime.parse(seg.path("start_date").getTextValue)
        val segName = seg.path("name").getTextValue
        val segStart = Seconds.secondsBetween(startTime, segStartTime).getSeconds
        val segDuration = seg.path("elapsed_time").getIntValue
        val segPrivate = seg.path("segment").path("private").getBooleanValue
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
      def pauseDuration(path: Seq[Double], time: Seq[Int]): Int = {
        def pauseDurationRecurse(start: Double, prev: Double, duration: Int, path: Seq[Double], time: Seq[Int]): Int = {
          path match {
            case head +: next +: tail if head - start <= maxPauseSpeed * duration && head - prev <= maxPauseSpeedImmediate * (time.tail.head - time.head) =>
              pauseDurationRecurse(start, head, duration + time.tail.head - time.head, path.tail, time.tail)
            case _ => duration
          }
        }
        pauseDurationRecurse(path.head, path.head, 0, path, time)
      }

      def computePauses(dist: Seq[Double], time: Seq[Int], pauses: Seq[Int]): Seq[Int] = {
        dist match {
          case head +: tail =>
            computePauses(tail, time.tail, pauseDuration(dist, time) +: pauses)
          case _ => pauses
        }
      }

      // ignore following too close
      def ignoreTooClose(pause: Int, pauses: Seq[Int], ret: Seq[Int]): Seq[Int] = {
        pauses match {
          case head +: tail =>
            if (head < pause) ignoreTooClose(pause - 1, tail, 1 +: ret)
            else ignoreTooClose(head, tail, head +: ret)
          case _ => ret
        }
      }

      import ActivityStreams._
      val pauses = computePauses(dist, time, Seq()).reverse // reverse to keep concat fast

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

    val pauseTimes = 0 +: pauses.map(_._2.time) :+ ActivityStreams.time.last

    val pauseRanges = pauseTimes zip (pauseTimes.drop(1) :+ pauseTimes.last)

    class SpeedStats(beg: Int, end: Int) {
      // TODO: compute sliding speed average



      val max10sec = 0
      val avg = 0
    }

    val sportsInRanges = for ((pBeg, pEnd) <- pauseRanges) yield {
      (pBeg, actId.sportName)
    }

    def findSport(time: Int) = {
      sportsInRanges.find(_._1 <= time).map(_._2).getOrElse(actId.sportName)
    }
    import ActivityStreams._
    // TODO: provide activity type with the split
    val events = (BegEvent(Stamp(0,0)) +: EndEvent(Stamp(time.last, dist.last)) +: laps.map(LapEvent)) ++ pauseEvents ++ segments

    // detect sports between pauses
    val sports = events.map(x => findSport(x.stamp.time))

    val eventsByTime = events.sortBy(_.stamp.time)

    ActivityEvents(actId, eventsByTime.toArray, sports.toArray, time, latlng, Seq(heartrate, cadence, watts, temp))
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

        val events = Main.getEventsFrom(authToken, id)

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