package com.github.opengrabeso.stravamat
package weather

import org.joda.time.{Seconds, DateTime => ZonedDateTime}

object GetTemperature {

  def apply(lon: Double, lat: Double): Double = {
    20
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
    val stream = temperaturePos.stream.mapValues { v =>
      apply(v.longitude, v.latitude).toInt
    }
    new DataStreamAttrib("temp", stream)
  }
}
