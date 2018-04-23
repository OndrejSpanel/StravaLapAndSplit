package com.github.opengrabeso.stravamat
package weather

import org.joda.time.{DateTime => ZonedDateTime}

object GetTemperature {

  def apply(lon: Double, lat: Double): Double = {
    20
  }


  def pickPositions(data: DataStreamGPS, distanceBetweenPoints: Double = 1000): DataStreamGPS = {
    // scan distance, each time going over
    def pickPositionsRecurse(processedDistance: Double, currDist: Double, todo: List[(ZonedDateTime, Double)], done: List[ZonedDateTime]): List[ZonedDateTime] = {
      todo match {
        case head :: tail =>
          val newDist = currDist + head._2
          if (newDist >= processedDistance + distanceBetweenPoints) {
            pickPositionsRecurse(newDist, newDist, tail, head._1 :: done)
          } else {
            pickPositionsRecurse(processedDistance, newDist, tail, done)
          }
        case _ =>
          done
      }
    }
    val times = pickPositionsRecurse(0, 0, data.distStream.toList, Nil).toSet
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
