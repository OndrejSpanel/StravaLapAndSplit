package net.suunto3rdparty
package moveslink

import Util._

import scala.annotation.tailrec

object MovesLinkUploader {

  @tailrec
  def processTimelines(lineGPS: List[Move], lineHRD: List[Move], processed: List[Move]): List[Move] = {
    def prependNonEmpty(move: Option[Move], list: List[Move]): List[Move] = {
      move.find(!_.isAlmostEmpty(30)).toList ++ list
    }

    if (lineGPS.isEmpty) {
      if (lineHRD.isEmpty) {
        processed
      } else {
        // HR moves without GPS info
        processTimelines(lineGPS, lineHRD.tail, prependNonEmpty(lineHRD.headOption, processed))
      }
    } else if (lineHRD.isEmpty) {
      processTimelines(lineGPS.tail, lineHRD, prependNonEmpty(lineGPS.headOption, processed))
    } else {
      val hrdMove = lineHRD.head
      val gpsMove = lineGPS.head

      val gpsBeg = gpsMove.startTime.get
      val gpsEnd = gpsMove.endTime.get

      val hrdBeg = hrdMove.startTime.get
      val hrdEnd = hrdMove.endTime.get

      if (gpsBeg >= hrdEnd) {
        // no match for hrd
        processTimelines(lineGPS, lineHRD.tail, prependNonEmpty(lineHRD.headOption, processed))
      } else if (hrdBeg > gpsEnd) {
        processTimelines(lineGPS.tail, lineHRD, prependNonEmpty(lineGPS.headOption, processed))
      } else {
        // some overlap, handle it
        // check if the activity start is the same within a tolerance

        // 10 percent means approx. 5 minutes from 1 hour (60 minutes)
        val tolerance = (lineGPS.head.duration max lineHRD.head.duration) * 0.10f

        if (timeDifference(gpsBeg, hrdBeg).abs <= tolerance) {
          // same beginning - drive by HRD
          // use from GPS only as needed by HRD
          // if GPS is only a bit longer than HDR, use it whole, unless there is another HDR waiting for it
          val (takeGPS, leftGPS) = if (timeDifference(gpsEnd, hrdEnd).abs <= tolerance && lineHRD.tail.isEmpty) {
            (Some(gpsMove), None)
          } else {
            gpsMove.span(hrdEnd)
          }

          val merged = takeGPS.map(m => (m.stream[DataStreamGPS], m)).map { sm =>
            val hrdAdjusted = sm._1.adjustHrd(hrdMove)
            hrdAdjusted.addStream(sm._2, sm._1)
          }

          println(s"Merged GPS ${takeGPS.map(_.toLog)} into ${hrdMove.toLog}")

          processTimelines(prependNonEmpty(leftGPS, lineGPS.tail), prependNonEmpty(merged, lineHRD.tail), processed)
        } else if (gpsBeg > hrdBeg) {
          val (takeHRD, leftHRD) = hrdMove.span(gpsBeg)

          processTimelines(lineGPS, prependNonEmpty(leftHRD, lineHRD.tail), prependNonEmpty(takeHRD, processed))

        } else  {
          val (takeGPS, leftGPS) = gpsMove.span(hrdBeg)

          processTimelines(prependNonEmpty(leftGPS, lineGPS.tail), lineHRD, prependNonEmpty(takeGPS, processed))
        }
      }

    }
  }

}