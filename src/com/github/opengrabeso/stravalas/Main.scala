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

  case class ActivityId(id: Long, name: String, startTime: DateTime) {
    def link: String = s"https://www.strava.com/activities/$id"
  }

  object ActivityId {
    def load(json: JsonNode): ActivityId = {
      val name = json.path("name").getTextValue
      val id = json.path("id").getLongValue
      val time = DateTime.parse(json.path("start_date").getTextValue)

      ActivityId(id, name, time)
    }
  }

  def lastActivity(authToken: String): ActivityId = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, authToken, "per_page=1")

    val responseJson = jsonMapper.readTree(request.execute().getContent)
    ActivityId.load(responseJson.get(0))
  }

  case class Stamp(time: Int, dist: Double)

  def niceDuration(duration: Int): String = {
    def round(x: Int, div: Int) = (x + div / 2) / div * div
    val minute = 60
    if (duration < minute) {
      s"${round(duration, 5)} sec"
    } else {
      val minutes = duration / minute
      val seconds = duration - minutes * minute
      if (duration < 5 * minute) {
        f"$minutes:${round(seconds, 10)}%2d min"
      } else {
        s"$minutes"
      }
    }
  }
  sealed abstract class Event {
    def stamp: Stamp
    def description: String
  }
  case class PauseEvent(duration: Int, stamp: Stamp) extends Event {
    def description: String = s"Pause ${niceDuration(duration)}"
  }
  case class LapEvent(stamp: Stamp) extends Event {
    def description: String = "Lap"
  }

  trait SegmentTitle {
    def isPrivate: Boolean
    def name: String
    def title = {
      val segTitle = if (isPrivate) "private segment" else "segment"
      s"$segTitle $name"
    }

  }
  case class StartSegEvent(name: String, isPrivate: Boolean, stamp: Stamp) extends Event with SegmentTitle {
    def description: String = s"Start $title"
  }
  case class EndSegEvent(name: String, isPrivate: Boolean, stamp: Stamp) extends Event with SegmentTitle {
    def description: String = s"End $title"
  }

  case class ActivityEvents(id: ActivityId, events: Array[Event], gps: Seq[(Double, Double)], attributes: Seq[(String, Seq[Int])])

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = ActivityId.load(responseJson)
    val startDateStr = responseJson.path("start_date").getTextValue
    val startTime = DateTime.parse(startDateStr)

    object ActivityStreams {
      private val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")

      private val streamTypes = allStreams.mkString(",")

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

      // TODO: remove start and end time, cannot edit them in any way

      val lapsInSeconds = lapTimes.map(lap => Seconds.secondsBetween(startTime, lap).getSeconds)

      lapsInSeconds.map(ActivityStreams.stampForTime)
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

    val stoppedDuration = 20
    val pauses = {
      import ActivityStreams._

      // compute speed from a distance stream
      val maxPauseSpeed = 0.2
      def pauseDuration(path: Seq[Double]): Int = {
        def pauseDurationRecurse(start: Double, duration: Int, path: Seq[Double]): Int = {
          path match {
            case head +: tail if head - start <= maxPauseSpeed * duration => pauseDurationRecurse(start, duration + 1, tail)
            case _ => duration
          }
        }
        pauseDurationRecurse(path.head, 0, path)
      }

      def computePausesReversed(dist: Seq[Double], pauses: Seq[Int]): Seq[Int] = {
        dist match {
          case head +: tail =>
            computePausesReversed(tail, pauseDuration(dist) +: pauses)
          case _ => pauses
        }
      }

      val pauses = computePausesReversed(dist, Seq()).reverse

      // ignore following too close
      def ignoreTooClose(pause: Int, pauses: Seq[Int], ret: Seq[Int]): Seq[Int] = {
        pauses match {
          case head +: tail =>
            if (head < pause) ignoreTooClose(pause - 1, tail, 1 +: ret)
            else ignoreTooClose(head, tail, head +: ret)
          case _ => ret
        }
      }

      val cleanedPauses = ignoreTooClose(0, pauses, Nil).reverse


      val minPause = 10
      val longPauses = (cleanedPauses.zipWithIndex zip stamps).filter { case ((p,i), stamp) =>
        p > minPause
      }.map {
        case ((p,i), stamp) => (p, stamp)
      }

      longPauses
    }

    val events = laps.map(LapEvent) ++ pauses.map(PauseEvent.tupled) ++ segments

    val eventsByTime = events.sortBy(_.stamp.time)

    import ActivityStreams._
    ActivityEvents(actId, eventsByTime.toArray, latlng, Seq(heartrate, cadence, watts, temp))
  }

  def downloadResult(authToken: String, id: String, op: String): Array[Byte] = {

    val events = getEventsFrom(authToken, id)

    val export = FitExport.export(events)

    export
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

    val ret = Main.downloadResult(authToken, id, op)


    resp.setContentType("application/octet-stream")
    resp.setStatus(200)
    resp.setHeader("Content-Disposition", s"attachment;filename=split_$id.fit")

    val out = resp.getOutputStream
    out.write(ret)
  }
}