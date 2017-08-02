package com.github.opengrabeso.stravalas
package requests
package push

import java.net.URLEncoder

import spark.{Request, Response}

import scala.util.Try
import scala.xml.{Elem, NodeSeq}
import org.joda.time.{DateTime => ZonedDateTime}
import DateTimeOps._

object PushStart extends DefineRequest("/push-start") with ActivityRequestHandler {

  override def urlPrefix = "push-"

  private def retryLogin(request: Request, resp: Response): Elem = {
    resp.cookie("authCode", "", 0) // delete the cookie
    <html>
      <head>
        {headPrefix}
        <title>Stravamat</title>
      </head>
      <body>
        {
          val hostname = request.host()
          val scheme = request.scheme()
          val secret = Main.secret
          val clientId = secret.appId
          val serverUri = scheme + "://" + hostname // Spark hostname seems to include port if needed
          val uri = "https://www.strava.com/oauth/authorize?"
          val action = uri + "client_id=" + clientId + "&response_type=code&redirect_uri=" + serverUri + "/push-login&scope=write,view_private&approval_prompt=force"
          <h3>Work in progress, use at your own risk.</h3>
            <p>
              Automated uploading and processing of Suunto data to Strava
            </p>
            <h4>Working</h4>
            <ul>
              <li>Merges Quest and GPS Track Pod data</li>
              <li>Splits GPS data as needed between Quest activities</li>
              <li>Corrects quest watch time inaccuracies</li>
            </ul>
            <h4>Planned (not working yet)</h4>
            <ul>
              <li>Edit lap information</li>
              <li>Show activity map</li>
              <li>Merge or split activities</li>
            </ul> :+ {
            if (!clientId.isEmpty) {
              <a href={action}>
                <img src="static/ConnectWithStrava.png" alt="Connect with STRAVA"></img>
              </a>
            } else {
              <p>Error:
                {secret.error}
              </p>
            }
          }
        }
        {bodyFooter}
      </body>
    </html>
  }

  def html(req: Request, resp: Response) = {
    val session = req.session()
    val port = req.queryParams("port").toInt
    // check stored oauth cookie
    val code = Option(req.cookie("authCode"))
    code.flatMap { code =>

      // DRY: LogIn
      val authResult = Try(Main.stravaAuth(code))
      //noinspection UnitInMap
      authResult.toOption.map { auth =>

        val stravaActivities = Main.recentStravaActivities(auth)

        // ignore anything older than oldest of recent Strava activities
        val ignoreBeforeLast = stravaActivities.lastOption.map(_.startTime) // oldest of the last 15 Strava activities
        val ignoreBeforeFirst = stravaActivities.headOption.map(_.startTime minusDays  14) // most recent on Strava - 2 weeks
        val ignoreBeforeNow = new ZonedDateTime() minusMonths 2 // max. 2 months

        val since = (Seq(ignoreBeforeNow) ++ ignoreBeforeLast ++ ignoreBeforeFirst).max

        resp.cookie("authCode", code, 3600 * 24 * 30) // 30 days
        session.attribute("auth", auth)
        resp.redirect(s"http://localhost:$port/auth?user=${URLEncoder.encode(auth.userId, "UTF-8")}&since=$since")
        NodeSeq.Empty
      }
    }.getOrElse {
      retryLogin(req, resp)
    }
  }
}
