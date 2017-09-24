package com.github.opengrabeso.stravamat
package requests
import java.io.InputStream

import org.joda.time.{DateTime => ZonedDateTime}
import com.google.appengine.api.taskqueue.DeferredTask
import RequestUtils._
import Main._
import shared.Util._

import scala.collection.immutable
/**
  * User specific cleanup, requires user access tokens for Strava */

@SerialVersionUID(10L)
case class UserCleanup(auth: Main.StravaAuthResult, before: ZonedDateTime) extends DeferredTask {
  override def run() = {

    val d = Storage.enumerate(namespace.stage, auth.userId)

    // clean activities before "before", as those are never listed to the user
    // verify they are already stored on Strava, if not, keep then until a global expiry cleanup will handle them
    val headers = d.flatMap { a =>
      Storage.load[ActivityHeader](namespace.stage, a, auth.userId)
    }.filter(_.id.startTime < before).toSeq.sortBy(_.id.startTime)

    for (h <- headers) {

      val timestamp = h.id.startTime.getMillis / 1000 - 1 * 3600

      val uri = "https://www.strava.com/api/v3/athlete/activities"
      val request = buildGetRequest(uri, auth.token, s"per_page=15&after=$timestamp")

      val stravaActivities = parseStravaActivities(request.execute().getContent)

      if (stravaActivities.exists(_ isMatching h.id)) {
        println(s"Will clean ${h.id}")
      }
    }

  }

}
