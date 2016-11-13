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
import scala.util.parsing.json.{JSON, JSONArray, JSONObject}

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

  class CC[T] {
    def unapply(a: Option[Any]): Option[T] = a.map(_.asInstanceOf[T])
  }

  object M extends CC[Map[String, Any]]

  object L extends CC[List[Any]]

  object S extends CC[String]

  object D extends CC[Double]

  object B extends CC[Boolean]

  def buildPostData(params: (String, String)*) = {
    params.map(p => s"${p._1}=${p._2}").mkString("&")
  }
}

class StravaAPI(authString: String) {

  import RequestUtils._
  import StravaAPI._

  // see https://strava.github.io/api/

  def athlete: String = {
    val request = buildGetRequest("https://www.strava.com/api/v3/athlete", authString, "")

    val result = request.execute().parseAsString
    result
  }

  def mostRecentActivityTime: Option[ZonedDateTime] = {
    // we might want to add parameters page=0, per_page=1
    val request = buildGetRequest("https://www.strava.com/api/v3/athlete/activities", authString, "")

    val result = request.execute().parseAsString

    val json = JSON.parseRaw(result)

    val times: Seq[ZonedDateTime] = json match {
      case Some(a: JSONArray) =>
        a.list.collect { case o: JSONObject =>
          val timeString = o.obj("start_date").toString
          val time = Try { ZonedDateTime.parse(timeString) }
          time.toOption
        }.flatten
      case _ =>
        Nil
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
    val request = buildDeleteRequest(s"https://www.strava.com/api/v3/activities/$id", authString, "")

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
      val request = buildGetRequest(s"https://www.strava.com/api/v3/uploads/$id", authString, "")
      request.getHeaders.set("Expect",Array("100-continue"))
      request.getHeaders.setAccept("*/*")

      val response = request.execute()
      val resultString = response.parseAsString
      val resultJson = JSON.parseFull(resultString)

      val activityId = Option(resultJson).map {
        case M(map) =>
          map.get("status") match {
            case S(status) if status == "Your activity is still being processed." =>
              Right(true)
            case _ =>
              map.get("activity_id") match {
                case D(actId) if actId.toLong != 0 =>
                  Left(actId.toLong)
                case _ =>
                  Right(false)
              }
          }
        case _ => Right(false)
      }
      val a = activityId
      try {
        a.get
      } catch {
        case x: NoSuchElementException => Right(false)
      }
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
      def binaryPart(name: String, filename: String) = {
        new MultipartContent.Part(
          new HttpHeaders().set("Content-Disposition", s"""attachment; name="$name"; filename="$filename""""),
          new ByteArrayContent("application/octet-stream", sendBytes)
        )
      }

      body.addPart(textPart("data_type", fileType))
      body.addPart(textPart("private", "1"))

      body.addPart(binaryPart("file", "file.fit.gz"))

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
        println(ex.getMessage)
        None
      case ex: Exception =>
        ex.printStackTrace()
        None
    }
  }

}
