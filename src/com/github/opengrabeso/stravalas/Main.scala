package com.github.opengrabeso.stravalas

import java.util
import java.util.logging.Logger

import com.google.api.client.http.{GenericUrl, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson.JacksonFactory
import com.google.appengine.repackaged.org.codehaus.jackson.map.ObjectMapper
import org.joda.time.{DateTime, Seconds}

import scala.collection.JavaConverters._
import DateTimeOps._

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

  case class ActivityId(id: Long, name: String)

  def lastActivity(authToken: String): ActivityId = {
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, authToken, "per_page=1")

    val responseJson = jsonMapper.readTree(request.execute().getContent)
    val name = responseJson.get(0).path("name").getTextValue
    val id = responseJson.get(0).path("id").getLongValue

    ActivityId(id, name)
  }

  case class ActivityLaps(laps: Array[String], pauses: Array[String])

  def getLapsFrom(authToken: String, id: String): ActivityLaps = {

    val laps = {
      val uri = s"https://www.strava.com/api/v3/activities/$id"
      val request = buildGetRequest(uri, authToken, "")

      val responseJson = jsonMapper.readTree(request.execute().getContent)

      val startDateStr = responseJson.path("start_date").getTextValue

      val startTime = DateTime.parse(startDateStr)

      val requestLaps = buildGetRequest(s"https://www.strava.com/api/v3/activities/$id/laps", authToken, "")

      val response = requestLaps.execute().getContent

      val lapsJson = jsonMapper.readTree(response)


      val lapTimes = (for (lap <- lapsJson.getElements.asScala) yield {
        val lapTimeStr = lap.path("start_date").getTextValue
        DateTime.parse(lapTimeStr)
      }).toSeq

      // TODO: remove start and end time, cannot edit them in any way

      val lapsInSeconds = lapTimes.map(lap => Seconds.secondsBetween(startTime, lap).getSeconds)

      lapsInSeconds.map(_.toString).toArray
    }

    val pauses = {
      val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")

      val streamTypes = allStreams.mkString(",")

      val uri = s"https://www.strava.com/api/v3/activities/$id/streams/$streamTypes"
      val request = buildGetRequest(uri, authToken, "")

      val response = request.execute().getContent

      val responseJson = jsonMapper.readTree(response)

      // detect where not moving based on "moving" stream

      val streams = responseJson.getElements.asScala
      val time = streams.filter(_.path("type").getTextValue == "time").toStream
      val moving = streams.filter(_.path("type").getTextValue == "moving").toStream
      val stoppedTimes = (moving.headOption, time.headOption) match {
        case (Some(m), Some(t)) =>
          val mData = m.path("data").asScala.map(_.asBoolean()).toSeq
          val tData = t.path("data").asScala.map(_.asInt()).toSeq
          val edges = mData zip mData.drop(1)
          (edges zip tData).filter(et => et._1._1 && !et._1._2).map(_._2)
        case (_, _) =>
          Seq()
      }

      // ignore following too close
      def ignoreTooClose(prev: Int, times: Seq[Int], ret: Seq[Int]): Seq[Int] = {
        times match {
          case head :: tail =>
            if (head < prev + 30) ignoreTooClose(head, tail, ret)
            else ignoreTooClose(head, tail, head +: ret)
          case _ => ret
        }
      }

      val ignoredClose = ignoreTooClose(0, stoppedTimes, Nil).reverse

      ignoredClose.map(_.toString).toArray // TODO: convert to TimeZone
    }
    ActivityLaps(laps, pauses)
  }

}
