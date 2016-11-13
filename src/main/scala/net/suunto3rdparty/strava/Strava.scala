package net.suunto3rdparty
package strava

import java.io._
import org.joda.time.{DateTime=>ZonedDateTime}
import java.util.zip.GZIPOutputStream

import org.apache.http.client.HttpResponseException
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import resource._
import Util._

import scala.util.{Failure, Success, Try}
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

  import StravaAPI._

  // see https://strava.github.io/api/

  private def authHeader = "Bearer " + authString

  def athlete: String = {
    val request = Request.Get(buildURI("athlete")).addHeader("Authorization", authHeader)

    val result = request.execute().returnContent()
    result.asString()
  }

  def mostRecentActivityTime: Option[ZonedDateTime] = {
    // we might want to add parameters page=0, per_page=1
    val request = Request.Get(buildURI("athlete/activities")).addHeader("Authorization", authHeader)

    val result = request.execute().returnContent()

    val json = JSON.parseRaw(result.asString())

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
    val request = Request.Delete(buildURI(s"activities/$id"))
      .useExpectContinue()
      .addHeader("Authorization", authHeader)
      .addHeader("Accept", "*/*")

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
      val request = Request.Get(buildURI(s"uploads/$id"))
        .useExpectContinue()
        .addHeader("Authorization", authHeader)
        .addHeader("Accept", "*/*")

      val response = request.execute()
      val content = response.returnContent()
      val resultString = content.asString()
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
      val body = MultipartEntityBuilder.create()
        .addTextBody("data_type", fileType) // case insensitive - possible values: fit, fit.gz, tcx, tcx.gz, gpx, gpx.gz
        .addTextBody("private", "1")
        .addBinaryBody("file", sendBytes, ContentType.APPLICATION_OCTET_STREAM, "file.fit.gz")
        .build()

      val request = Request.Post(buildURI("uploads"))
        .useExpectContinue()
        .addHeader("Authorization", authHeader)
        .addHeader("Accept", "*/*")
        .body(body)

      val response = request.execute()
      val content = response.returnContent()

      val resultString = content.asString()

      // we expect to receive 201

      val resultJson = JSON.parseFull(resultString)
      val uploadId = Option(resultJson).flatMap {
        case M(map) =>
          map.get("id") match {
            case D(id) =>
              println(s"  upload id ${id.toLong}")
              Some(id.toLong)
            case _ =>
              None
          }
        case _ => None
      }
      uploadId

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
