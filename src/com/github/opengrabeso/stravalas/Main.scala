package com.github.opengrabeso.stravalas

import java.util

import com.google.api.client.http.{GenericUrl, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory

object Main {
  private val transport = new NetHttpTransport()
  private val jsonFactory = new JacksonFactory()

  def someComputation: String = "I have computed this!"

  def doComputation(i: Int): String = ("*" + i.toString) * 2

  def getLapsFrom(authToken: String, id: String): Array[Double] = {

    val requestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
      override def initialize(request: HttpRequest) = request.setParser(new JsonObjectParser(jsonFactory))
    })

    val json = new util.HashMap[String, String]()

    json.put("client_id", "8138") // TODO: DRY
    json.put("client_secret", secret) // TODO: DRY
    json.put("code", authToken)

    val content = new JsonHttpContent(new JacksonFactory(), json)

    val request = requestFactory.buildPostRequest(new GenericUrl("https://www.strava.com/oauth/token"), content)
    val response = request.execute() // TODO: async?

    val responseString = scala.io.Source.fromInputStream(response.getContent).mkString

    Array(0.0, 0.5, 1.0)
  }

  def secret: String = {
    val secretStream = Main.getClass.getResourceAsStream("/secret.txt")
    scala.io.Source.fromInputStream(secretStream).mkString
  }
}
