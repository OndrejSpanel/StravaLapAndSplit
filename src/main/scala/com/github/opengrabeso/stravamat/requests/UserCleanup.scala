package com.github.opengrabeso.stravamat
package requests
import java.io.InputStream

import org.joda.time.{DateTime => ZonedDateTime}
import com.google.appengine.api.taskqueue.DeferredTask
import RequestUtils._
import Main._
import shared.Util._

import scala.util.control.Breaks._

/**
  * User specific cleanup, requires user access tokens for Strava */

@SerialVersionUID(10L)
case class UserCleanup(auth: Main.StravaAuthResult, before: ZonedDateTime) extends DeferredTask {
  override def run(): Unit = {

    val d = Storage.enumerate(namespace.stage, auth.userId)

    // clean activities before "before", as those are never listed to the user
    // verify they are already stored on Strava, if not, keep then until a global expiry cleanup will handle them
    val headers = d.flatMap { a =>
      Storage.load[ActivityHeader](a._1).map(a -> _)
    }

    val oldHeaders = headers.filter(_._2.id.startTime < before).toSeq.sortBy(_._2.id.startTime)

    // TODO: check and cleanup for activities older than the history
    val (_, oldStravaActivities) = Main.recentStravaActivitiesHistory(auth, 2)

    val headersToClean = oldHeaders.flatMap(h => oldStravaActivities.find(_ isMatching h._2.id).map(h -> _))
    /*
    val timestamp = h.id.startTime.getMillis / 1000 - 1 * 3600
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, auth.token, s"per_page=15&after=$timestamp")
    val stravaActivities = parseStravaActivities(request.execute().getContent)
    */

    for ((((file,_), h), onStrava) <- headersToClean) {
      println(s"Cleaning stage file $onStrava ${h.id} $file")
      Storage.delete(file)
    }

  }

}
