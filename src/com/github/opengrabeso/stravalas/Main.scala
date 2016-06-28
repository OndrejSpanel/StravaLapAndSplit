package com.github.opengrabeso.stravalas

import java.util
import java.util.logging.{Level, Logger}

import com.google.api.client.http.{GenericUrl, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson.JacksonFactory
import com.google.appengine.repackaged.org.codehaus.jackson.map.ObjectMapper
import org.joda.time.DateTime

import DateTimeOps._

object Main {
  private val transport = new NetHttpTransport()
  private val jsonFactory = new JacksonFactory()
  private val jsonMapper = new ObjectMapper()

  private val logger = Logger.getLogger(Main.getClass.getName)

  private val requestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
    override def initialize(request: HttpRequest) = request.setParser(new JsonObjectParser(jsonFactory))
  })

  def buildGetRequest(uri: String, authToken: String): HttpRequest = {
    val request = requestFactory.buildGetRequest(new GenericUrl(uri))
    authorizeHeaders(request, authToken)
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

    logger.log(Level.INFO, s"client_id $clientId, code $code")

    val content = new JsonHttpContent(new JacksonFactory(), json)

    val request = requestFactory.buildPostRequest(new GenericUrl("https://www.strava.com/oauth/token"), content)
    val response = request.execute() // TODO: async?

    val responseJson = jsonMapper.readTree(response.getContent)
    val token = responseJson.path("access_token").getTextValue

    logger.log(Level.INFO, s"token $token")

    token

  }

  def authorizeHeaders(request: HttpRequest, authToken: String) = {
    val headers = request.getHeaders
    headers.put("Authorization:", s"Bearer $authToken")
  }

  def athlete(authToken: String): String = {
    val uri = s"https://www.strava.com/api/v3/athlete"
    val request = buildGetRequest(uri, authToken)

    logger.log(Level.INFO, s"GET uri $uri")
    logger.log(Level.INFO, s"request headers ${request.getHeaders.toString}")

    val response = request.execute().getContent

    val json = jsonMapper.readTree(response)

    val firstname = json.path("firstname").getTextValue
    val lastname = json.path("lastname").getTextValue
    firstname + " " + lastname
  }

  case class ActivityId(id: Long, name: String)

  def lastActivity(authToken: String): ActivityId = {
    val uri = "https://www.strava.com/api/v3/athlete/activities?per_page=1"
    val request = buildGetRequest(uri, authToken)

    val responseJson = jsonMapper.readTree(request.execute().getContent)
    val name = responseJson.get(0).path("name").getTextValue
    val id = responseJson.get(0).path("id").getLongValue

    ActivityId(id, name)
  }

  def getLapsFrom(authToken: String, id: String): Array[String] = {

    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken)

    logger.log(Level.INFO, s"GET uri $uri")
    logger.log(Level.INFO, s"request headers ${request.getHeaders.toString}")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val startDateStr = responseJson.path("start_date").getTextValue

    val startTime = DateTime.parse(startDateStr)

    val requestLaps = buildGetRequest(s"https://www.strava.com/api/v3/activities/$id/laps", authToken)

    val response = requestLaps.execute().getContent

    val lapsJson = jsonMapper.readTree(response)

    import scala.collection.JavaConverters._

    val lapTimes = (for (lap <- lapsJson.getElements.asScala) yield {
      val lapTimeStr = lap.path("start_date").getTextValue
      DateTime.parse(lapTimeStr)
    }).toSeq

    val allLaps = if (lapTimes.nonEmpty && lapTimes.head > startTime) startTime +: lapTimes else lapTimes

    allLaps.map(_.toString).toArray
  }

}
