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
import DateTimeOps._
import com.google.appengine.repackaged.org.codehaus.jackson.JsonNode
import com.google.appengine.repackaged.org.codehaus.jackson.map.ObjectMapper
import org.joda.time.format.PeriodFormatterBuilder

import com.garmin.fit
import com.garmin.fit._

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

  case class ActivityId(id: Long, name: String) {
    def link: String = s"https://www.strava.com/activities/$id"
  }

  object ActivityId {
    def load(json: JsonNode): ActivityId = {
      val name = json.path("name").getTextValue
      val id = json.path("id").getLongValue

      ActivityId(id, name)
    }
  }

  def lastActivity(authToken: String): ActivityId = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, authToken, "per_page=1")

    val responseJson = jsonMapper.readTree(request.execute().getContent)
    ActivityId.load(responseJson.get(0))
  }

  case class Stamp(time: Int, dist: Double)

  case class Event(kind: String, stamp: Stamp)

  case class ActivityEvents(id: ActivityId, events: Array[Event])

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = ActivityId(responseJson.path("id").getLongValue, responseJson.path("name").getTextValue)
    val startDateStr = responseJson.path("start_date").getTextValue
    val startTime = DateTime.parse(startDateStr)

    object ActivityStreams {
      private val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")

      private val streamTypes = allStreams.mkString(",")

      private val uri = s"https://www.strava.com/api/v3/activities/$id/streams/$streamTypes"
      private val request = buildGetRequest(uri, authToken, "")

      private val response = request.execute().getContent

      private val responseJson = jsonMapper.readTree(response)

      // detect where not moving based on "moving" stream

      val streams = responseJson.getElements.asScala
      val time = streams.filter(_.path("type").getTextValue == "time").toStream
      val dist = streams.filter(_.path("type").getTextValue == "distance").toStream

      val tData = time.head.path("data").asScala.map(_.asInt()).toSeq
      val dData = dist.head.path("data").asScala.map(_.asDouble()).toSeq

      val stamps = (tData zip dData).map((Stamp.apply _).tupled)

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

    val segments = {
      val segmentList = responseJson.path("segment_efforts").asScala
      segmentList.flatMap {seg =>
        val segStartTime = DateTime.parse(seg.path("start_date").getTextValue)
        val segName = seg.path("name")
        val segStart = Seconds.secondsBetween(startTime, segStartTime).getSeconds
        val segDuration = seg.path("elapsed_time").getIntValue
        val segPrivate = seg.path("segment").path("private").getBooleanValue
        val title = if (segPrivate) "private segment" else "segment"
        Seq(
          Event(s"Start $title $segName", ActivityStreams.stampForTime(segStart)),
          Event(s"End $title $segName", ActivityStreams.stampForTime(segStart + segDuration))
        )
      }
    }

    val pauses = {
      import ActivityStreams._
      val moving = streams.filter(_.path("type").getTextValue == "moving").toStream
      val stoppedTimes = (moving.headOption, time.headOption, dist.headOption) match {
        case (Some(m), Some(t), Some(d)) =>
          val mData = m.path("data").asScala.map(_.asBoolean()).toSeq
          val tData = t.path("data").asScala.map(_.asInt()).toSeq
          val dData = d.path("data").asScala.map(_.asDouble()).toSeq
          val edges = mData zip mData.drop(1)
          (edges zip stamps).filter(et => et._1._1 && !et._1._2).map(_._2)
        case _ =>
          Seq()
      }

      // ignore following too close
      def ignoreTooClose(prev: Int, times: Seq[Stamp], ret: Seq[Stamp]): Seq[Stamp] = {
        times match {
          case head :: tail =>
            if (head.time < prev + 30) ignoreTooClose(head.time, tail, ret)
            else ignoreTooClose(head.time, tail, head +: ret)
          case _ => ret
        }
      }

      val ignoredClose = ignoreTooClose(0, stoppedTimes, Nil).reverse

      ignoredClose
    }

    val events = laps.map(Event("Lap", _)) ++ pauses.map(Event("Pause", _)) ++ segments

    val eventsByTime = events.sortBy(_.stamp.time)

    ActivityEvents(actId, eventsByTime.toArray)
  }


  def process(laps: ActivityEvents): String = {
    "A file data to download"
  }

  def downloadResult(authToken: String, id: String, op: String): Array[Byte] = {

    val events = getEventsFrom(authToken, id)

    val ret = s"Testing download ${events.id.name}".getBytes
    ret
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