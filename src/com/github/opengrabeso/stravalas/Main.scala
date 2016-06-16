package com.github.opengrabeso.stravalas

import java.util

import com.google.api.client.http.{GenericUrl, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.appengine.repackaged.org.codehaus.jackson.map.ObjectMapper
import org.joda.time.DateTime

object Main {
  private val transport = new NetHttpTransport()
  private val jsonFactory = new JacksonFactory()
  private val jsonMapper = new ObjectMapper()

  private val requestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
    override def initialize(request: HttpRequest) = request.setParser(new JsonObjectParser(jsonFactory))
  })

  def someComputation: String = "I have computed this!"

  def doComputation(i: Int): String = ("*" + i.toString) * 2

  def secret: String = {
    val secretStream = Main.getClass.getResourceAsStream("/secret.txt")
    scala.io.Source.fromInputStream(secretStream).mkString
  }

  def stravaAuth(code: String): String = {

    val json = new util.HashMap[String, String]()

    json.put("client_id", "8138") // TODO: DRY
    json.put("client_secret", secret) // TODO: DRY
    json.put("code", code)

    val content = new JsonHttpContent(new JacksonFactory(), json)

    val request = requestFactory.buildPostRequest(new GenericUrl("https://www.strava.com/oauth/token"), content)
    val response = request.execute() // TODO: async?

    val responseJson = jsonMapper.readTree(response.getContent)
    val token = responseJson.path("access_token").getTextValue
    token

  }

  def getLapsFrom(authToken: String, id: String): Array[String] = {
    def authorizeHeaders(request: HttpRequest) = {
      val headers = request.getHeaders
      headers.put("Authorization:", s"Bearer $authToken")
    }

    val request = requestFactory.buildGetRequest(new GenericUrl(s"https://www.strava.com/api/v3/activities/$id"))

    authorizeHeaders(request)

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val startDateStr = responseJson.path("start_date").getTextValue

    val startTime = DateTime.parse(startDateStr)

    val requestLaps = requestFactory.buildGetRequest(new GenericUrl(s"https://www.strava.com/api/v3/activities/$id/laps"))
    authorizeHeaders(requestLaps)

    val lapsJson = jsonMapper.readTree(requestLaps.execute().getContent)

    import scala.collection.JavaConverters._

    val laps = for (lap <- lapsJson.getElements.asScala) yield {
      val lapTimeStr = lap.path("start_date").getTextValue
      val lapTime = DateTime.parse(lapTimeStr)
      lapTime.toString()
    }
    laps.toArray
  }

}
