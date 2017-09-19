package com.github.opengrabeso.stravamat
package requests

import Main._
import shared.Util._
import spark.{Request, Response}
import com.google.appengine.api.taskqueue._

object Process extends DefineRequest.Post("/process") with ParseFormData with UploadResults {

  private def follows(first: ActivityEvents, second: ActivityEvents) = {
    val secondGap = second.secondsInActivity(first.endTime)
    // if second starts no more than 10 minutes after the first ends, merge them
    secondGap < 10 && secondGap > -10 * 60
  }

  private def mergeConsecutive(events: List[ActivityEvents]): List[ActivityEvents] = {
    @scala.annotation.tailrec
    def mergeConsecutiveRecurse(todo: List[ActivityEvents], done: List[ActivityEvents]): List[ActivityEvents] = {
      todo match {
        case first :: second :: tail if first.id.sportName == second.id.sportName && follows(first, second) =>
          mergeConsecutiveRecurse(tail, first.merge(second) :: done)
        case first :: tail =>
          mergeConsecutiveRecurse(tail, first :: done)
        case Nil =>
          done
      }
    }
    mergeConsecutiveRecurse(events, Nil).reverse
  }

  def mergeAndUpload(auth: Main.StravaAuthResult, toMerge: Vector[ActivityEvents], sessionId: String): Int = {
    if (toMerge.nonEmpty) {

      val (gpsMoves, attrMovesRaw) = toMerge.partition(_.hasGPS)

      val timeOffset = Settings(auth.userId).questTimeOffset
      val ignoreDuration = 30

      val attrMoves = attrMovesRaw.map(_.timeOffset(-timeOffset))

      def filterIgnored(x: ActivityEvents) = x.isAlmostEmpty(ignoreDuration)

      val timelineGPS = gpsMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)
      val timelineAttr = mergeConsecutive(attrMoves.toList.filterNot(filterIgnored).sortBy(_.startTime))

      val merged = moveslink.MovesLinkUploader.processTimelines(timelineGPS, timelineAttr)

      uploadMultiple(merged)(auth, sessionId)

    } else 0
  }


  override def html(request: Request, resp: Response) = {

    val session = request.session()
    implicit val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sessionId = session.attribute[String]("sid")

    assert(sessionId != null)

    val ops = activities(request)._1

    val toMerge = ops.flatMap { op =>
      Storage.load[ActivityHeader, ActivityEvents](Main.namespace.stage, op.filename, auth.userId).map(_._2)
    }

    val uploadCount = mergeAndUpload(auth, toMerge, sessionId)

    countResponse(uploadCount)

  }
}