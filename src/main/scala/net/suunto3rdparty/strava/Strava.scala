package net.suunto3rdparty
package strava

import java.io._

import org.joda.time.{DateTime => ZonedDateTime}
import java.util.zip.GZIPOutputStream

import com.google.api.client.http._
import resource._
import Util._
import com.github.opengrabeso.stravalas.RequestUtils

import scala.util.Try

case class StravaAPIParams(appId: Int, clientSecret: String, code: Option[String])

object StravaAPI {
  val localTest = false

  val stravaSite = "www.strava.com"
  val stravaRootURL = "/api/v3/"
  val stravaRoot = "https://" + stravaSite + stravaRootURL

  def buildURI(path: String): String = {
    if (!localTest) stravaRoot + path
    else "http://localhost/JavaEE-war/webresources/generic/"
  }

  def buildPostData(params: (String, String)*) = {
    params.map(p => s"${p._1}=${p._2}").mkString("&")
  }
}

class StravaAPI(authString: String) {

  import RequestUtils._
  import StravaAPI._

  // see https://strava.github.io/api/

  def athlete: String = {
    val request = buildGetRequest(buildURI("athlete"), authString, "")

    val result = request.execute().parseAsString
    result
  }

  def mostRecentActivityTime: Option[ZonedDateTime] = {
    // we might want to add parameters page=0, per_page=1
    val request = buildGetRequest(buildURI("athlete/activities"), authString, "")

    val result = request.execute().getContent

    val json = jsonMapper.readTree(result)

    val times = (0 until json.size).flatMap { i =>
      val start_date = Option(json.get(i).path("start_date").textValue)
      start_date match {
        case Some(timeString) =>
          val time = Try {
            ZonedDateTime.parse(timeString)
          }
          time.toOption
        case _ =>
          Nil
      }
    }

    val mostRecentTime = if (times.nonEmpty) Some(times.max) else None

    mostRecentTime
  }

  /*
    * @return upload id (use to check status with uploads/:id)
    * */
  def upload(move: Move): Option[Long] = {
    val fitFormat = true
    if (fitFormat) {
      val moveBytes = fit.Export.toBuffer(move)
      uploadRawFileGz(moveBytes, "fit.gz")
    } else {
      val baos = new ByteArrayOutputStream()
      managed(new GZIPOutputStream(baos)).foreach(tcx.Export.toOutputStream(_, move))
      uploadRawFile(baos.toByteArray, "tcx.gz")
    }
  }

  def deleteActivity(id: Long): Unit = {
    val request = buildDeleteRequest(buildURI(s"activities/$id"), authString, "")

    request.execute()
  }

  def uploadRawFileGz(moveBytesOriginal: Array[Byte], fileType: String): Option[Long] = {

    val baos = new ByteArrayOutputStream()
    managed(new GZIPOutputStream(baos)).foreach(_.write(moveBytesOriginal))

    uploadRawFile(baos.toByteArray, fileType)
  }

  /**
  * @return Either[id, pending] pending is true if the result is not definitive yet
    */
  def activityIdFromUploadId(id: Long): Either[Long, Boolean] = {
    try {
      val request = buildGetRequest(buildURI(s"uploads/$id"), authString, "")
      request.getHeaders.set("Expect",Array("100-continue"))
      request.getHeaders.setAccept("*/*")

      val response = request.execute()
      val resultJson = jsonMapper.readTree(response.getContent)

      val activityId = (Option(resultJson.path("status").textValue), Option(resultJson.path("activity_id").numberValue)) match {
        case (Some(status), _) if status == "Your activity is still being processed." =>
          Right(true)
        case (_, Some(actId)) if actId.longValue != 0 =>
          Left(actId.longValue)
        case _ =>
          Right(false)
      }
      activityId
    } catch {
      case ex: HttpResponseException if ex.getStatusCode == 404 =>
        Right(false)
      case ex: Exception =>
        ex.printStackTrace()
        Right(false)
    }

  }

  def uploadRawFile(sendBytes: Array[Byte], fileType: String): Option[Long] = {

    try {
      // see https://strava.github.io/api/v3/uploads/ -
      val body = new MultipartContent()

      def textPart(name: String, value: String) = {
        new MultipartContent.Part(
          new HttpHeaders().set("Content-Disposition", s"""name="$name""""),
          ByteArrayContent.fromString("text/plain", value)
        )
      }
      def binaryPart(name: String, filename: String, bytes: Array[Byte]) = {
        new MultipartContent.Part(
          new HttpHeaders().set("Content-Disposition", s"""attachment; name="$name"; filename="$filename""""),
          new ByteArrayContent("application/octet-stream", bytes)
        )
      }

      body.addPart(textPart("data_type", fileType))
      body.addPart(textPart("private", "1"))

      body.addPart(binaryPart("file", "file." + fileType, sendBytes))

      val request = buildPostRequest(buildURI("uploads"), authString, "", body)
      request.getHeaders.set("Expect",Array("100-continue"))
      request.getHeaders.setAccept("*/*")

      val response = request.execute()
      val resultString = response.getContent

      // we expect to receive 201

      val resultJson = jsonMapper.readTree(resultString)
      val id = Option(resultJson.path("id").numberValue)
      id.map(_.longValue)
    } catch {
      case ex: HttpResponseException =>
        // we expect to receive error 400 - duplicate activity
        if (ex.getMessage.contains("duplicate of activity")) {
          // TODO: parse using regex, print info about a duplicate
          println("Msg: " + ex.getMessage)
        } else {
          println("Msg: " + ex.getMessage)
        }
        None
      case ex: Exception =>
        println("Error uploading")
        ex.printStackTrace()
        None
    }
  }

}
