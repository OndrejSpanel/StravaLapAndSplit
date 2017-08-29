package com.github.opengrabeso.stravamat
package requests
package push

import java.net.URLEncoder

import spark.{Request, Response}

import scala.xml.NodeSeq
import org.joda.time.{DateTime => ZonedDateTime}
import shared.Util._

object PushStart extends DefineRequest("/push-start") {

  def storedQueryParam(req: Request, prefix: String, name: String): String = {
    val session = req.session()
    val p = req.queryParams(name)
    if (p != null) {
      session.attribute(prefix + name, p)
      p
    } else {
      session.attribute[String](prefix + name)
    }
  }

  def html(req: Request, resp: Response) = withAuth(req, resp) { auth =>
    val session = req.session()
    // We need the ID to be unique for a given user, timestamps seems reasonable for this.
    // Normal web app session ID is not unique, sessions get reused.
    val sessionId = storedQueryParam(req, "push-", "session")
    val port = storedQueryParam(req, "push-", "port").toInt

    val stravaActivities = Main.recentStravaActivities(auth)
    session.attribute("stravaActivities", stravaActivities)

    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeLast = stravaActivities.lastOption.map(_.startTime) // oldest of the last 15 Strava activities
    val ignoreBeforeFirst = stravaActivities.headOption.map(_.startTime minusDays  14) // most recent on Strava - 2 weeks
    val ignoreBeforeNow = new ZonedDateTime() minusMonths 2 // max. 2 months

    val since = (Seq(ignoreBeforeNow) ++ ignoreBeforeLast ++ ignoreBeforeFirst).max

    resp.redirect(s"http://localhost:$port/auth?user=${URLEncoder.encode(auth.userId, "UTF-8")}&since=$since&session=$sessionId")
    NodeSeq.Empty
  }
}
