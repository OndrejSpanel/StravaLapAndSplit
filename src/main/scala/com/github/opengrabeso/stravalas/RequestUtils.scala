package com.github.opengrabeso.stravalas

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.http.{GenericUrl, HttpContent, HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory

object RequestUtils {
  private val transport = new NetHttpTransport()
  val jsonFactory = new JacksonFactory()
  val jsonMapper = new ObjectMapper()

  val requestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
    override def initialize(request: HttpRequest) = request.setParser(new JsonObjectParser(jsonFactory))
  })

  def buildPostRequest(uri: String, authToken: String, parameters: String, content: HttpContent): HttpRequest = {
    val request = requestFactory.buildPostRequest(new GenericUrl(uri + "?access_token=" + authToken + "&" + parameters), content)
    request
  }

  def buildGetRequest(uri: String, authToken: String, parameters: String): HttpRequest = {
    val request = requestFactory.buildGetRequest(new GenericUrl(uri + "?access_token=" + authToken + "&" + parameters))
    request
  }

  def buildDeleteRequest(uri: String, authToken: String, parameters: String): HttpRequest = {
    val request = requestFactory.buildDeleteRequest(new GenericUrl(uri + "?access_token=" + authToken + "&" + parameters))
    request
  }

}
