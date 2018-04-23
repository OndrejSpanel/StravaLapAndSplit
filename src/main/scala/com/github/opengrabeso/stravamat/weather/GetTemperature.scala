package com.github.opengrabeso.stravamat
package weather

import org.joda.time.{Seconds, DateTime => ZonedDateTime}
import shared.Util._

import scala.collection.immutable.SortedMap
import scala.util.Try

object GetTemperature {

  def apply(lon: Double, lat: Double, time: ZonedDateTime): Option[Double] = {
    // https://darksky.net/dev/docs
    val secret = Main.secret.darkSkySecret
    val timePar = time.toString().replace(".000", "")
    val requestUrl = s"https://api.darksky.net/forecast/$secret/$lat,$lon,$timePar?units=si&exclude=hourly,daily,minutely,flags"

    val request = RequestUtils.buildGetRequest(requestUrl)

    Try {
      val response = request.execute() // TODO: async ?

      val responseJson = RequestUtils.jsonMapper.readTree(response.getContent)

      val tempJson = responseJson.path("currently").path("temperature")

      // TODO: cache darksky.net responses
      tempJson.asDouble
    }.toOption
  }


  def pickPositions(data: DataStreamGPS, distanceBetweenPoints: Double = 1000, timeBetweenPoints: Double = 3600): DataStreamGPS = {
    // scan distance, each time going over
    def pickPositionsRecurse(lastPoint: Option[(ZonedDateTime,GPSPoint)], todo: List[(ZonedDateTime, GPSPoint)], done: List[ZonedDateTime]): List[ZonedDateTime] = {
      todo match {
        case head :: tail =>
          if (lastPoint.forall { case (time, pos) =>
            pos.distance(head._2) > distanceBetweenPoints ||
            Seconds.secondsBetween(time, head._1).getSeconds > timeBetweenPoints
          }) {
            pickPositionsRecurse(Some(head), tail, head._1 :: done)
          } else {
            pickPositionsRecurse(lastPoint, tail, done)
          }
        case _ =>
          done
      }
    }
    val times = pickPositionsRecurse(None, data.stream.toList, Nil).toSet
    val positions = data.stream.filterKeys(times.apply)
    data.pickData(positions)
  }

  def forPositions(temperaturePos: DataStreamGPS): DataStreamAttrib = {
    val stream = temperaturePos.stream.toSeq.flatMap { case (k, v) =>
      val result = apply(v.longitude, v.latitude, k).map(_.round.toInt)
      result.map(k -> _)
    }
    new DataStreamAttrib("temp", SortedMap(stream:_*))
  }
}
