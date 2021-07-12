package com.github.opengrabeso.mixtio

import java.net.URLEncoder

import Main._
import com.google.api.client.http.HttpResponseException
import spark.{Request, Response, Session}

import scala.util.Try
import scala.xml.NodeSeq

sealed trait Method
object Method {
  case object Get extends Method
  case object Put extends Method
  case object Post extends Method
  case object Delete extends Method

}

case class Handle(value: String, method: Method = Method.Get)

object DefineRequest {
  abstract class Post(handleUri: String) extends DefineRequest(handleUri, method = Method.Post)
}

abstract class DefineRequest(val handleUri: String, val method: Method = Method.Get) {

  // some actions (logout) may have their URL prefixed to provide a specific functionality

  def apply(request: Request, resp: Response): AnyRef = {

    import com.google.appengine.api.utils.SystemProperty

    if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Development) {
      // logging on production server is counter-productive, logs are already sorted by request
      println(s"Request ${request.url()}")
    }
    val nodes = html(request, resp)
    if (nodes.nonEmpty) {
      nodes.head match {
        case <html>{_*}</html> =>
          val docType = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd" >"""
          docType + nodes.toString
        case _ =>
          resp.`type`("text/xml; charset=utf-8")
          val xmlPrefix = """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
          xmlPrefix + nodes.toString
      }
    } else resp
  }

  def html(request: Request, resp: Response): NodeSeq

  def cond(boolean: Boolean) (nodes: NodeSeq): NodeSeq = {
    if (boolean) nodes else Nil
  }

  def headPrefix: NodeSeq = {
    <meta charset="utf-8"/>
    <link rel="icon" href="static/favicon.ico"/>
  }

  def bodyHeader(auth: Main.StravaAuthResult): NodeSeq = {
    <div id="header" style="background-color:#fca;overflow:auto">
    <table>
      <tr>
        <td>
          <a href="/"><img src="static/stravaUpload64.png"></img></a>
        </td>
        <td>
          <table>
            <tr>
              <td>
                <a href="/">{appName}</a>
              </td>
            </tr>
            <tr>
              <td>
                Athlete:
                <a href={s"https://www.strava.com/athletes/${auth.id}"}>
                  {auth.name}
                </a>
              </td>
            </tr>
          </table>
        </td>
        <td>
        <form action={"logout"}>
          <input type="submit" value ="Log Out"/>
        </form>
        </td>
      </tr>
    </table>
    </div>
    <p></p>
  }

  def bodyFooter: NodeSeq = {
    <p></p>
    <div id="footer" style="background-color:#fca;overflow:auto">
      <a href="http://labs.strava.com/" id="powered_by_strava" rel="nofollow">
        <img align="left" src="static/api_logo_pwrdBy_strava_horiz_white.png" style="max-height:46px"/>
      </a>
      <p style="color:#fff"><a href="https://darksky.net/poweredby/" style="color:#fff">Powered by Dark Sky</a> © 2016 - 2018 <a href={s"https://github.com/OndrejSpanel/${gitHubName}"} style="color:inherit">Ondřej Španěl</a></p>
      <div/>
    </div>
  }

  def uniqueSessionId(session: Session): String = {
    // stored using storedQueryParam
    session.attribute[String]("push-session")
  }

  def storeAuth(session: Session, auth: StravaAuthResult) = {
    session.attribute("auth", auth)
  }
  def performAuth(code: String, resp: Response, session: Session): Try[StravaAuthResult] = {
    val authResult = Try(Main.stravaAuth(code))
    authResult.foreach { auth =>
      println("Login done, create authCode cookie")
      resp.cookie("authCode", code, 3600 * 24 * 30) // 30 days
      resp.cookie("sessionId", auth.sessionId) // session cookie - no expiry
      storeAuth(session, auth)
    }
    if (authResult.isFailure) {
      println("Strava authentication failed")
    }
    authResult
  }

  def withAuth(req: Request, resp: Response)(body: StravaAuthResult => NodeSeq): NodeSeq = {
    val session = req.session()
    val auth = session.attribute[StravaAuthResult]("auth")
    if (auth == null || auth.refreshToken == null) {
      val codePar = Option(req.queryParams("code"))
      val statePar = Option(req.queryParams("state")).filter(_.nonEmpty)
      codePar.fold {
        val code = Option(req.cookie("authCode"))
        code.flatMap { code =>
          println("withAuth performAuth")
          performAuth(code, resp, session).toOption.map(body)
        }.getOrElse {
          println("withAuth loginPage")
          loginPage(req, resp, req.url, Option(req.queryString))
        }
      } { code =>
        if (performAuth(code, resp, session).isSuccess) {
          println("withAuth performAuth redirect")
          resp.redirect(req.url() + statePar.fold("")("?" + _))
          NodeSeq.Empty
        } else {
          println("withAuth performAuth loginPage")
          loginPage(req, resp, req.url, statePar)
        }
      }
    } else {
      // if code is received, login was done and redirected to this URL
      val codePar = Option(req.queryParams("code"))
      codePar.fold {
        // try issuing the request, if failed, perform auth as needed
        val res = Try {
          println("withAuth codePar stravaAuthRefresh")
          val newAuth = stravaAuthRefresh(auth)
          resp.cookie("authCode", newAuth.code, 3600 * 24 * 30) // 30 days
          resp.cookie("sessionId", auth.sessionId) // session cookie - no expiry
          storeAuth(session, newAuth)
          body(newAuth)
        }
        val resRecovered = res.recover {
          case err: HttpResponseException if err.getStatusCode == 401 => // unauthorized
            val query = req.queryString
            println("withAuth resRecovered loginPage")
            loginPage(req, resp, req.url, Option(query))
        }
        resRecovered.get
      } { code =>
        // called as a callback, process the token
        println("withAuth performAuth callback")
        performAuth(code, resp, session).map(body).get
      }

      // first refresh the token
      // TODO: consider skipping this if the token is very fresh
      //performAuth(code, resp, session).toOption.map(body)
    }
  }

  def loginPage(request: Request, resp: Response, afterLogin: String, afterLoginParams: Option[String]): NodeSeq = {
    println("Login page, delete authCode cookie")
    resp.cookie("authCode", "", 0) // delete the cookie
    <html>
      <head>
        {headPrefix}
        <title>{appName}</title>
      </head>
      <body>
        {
        val secret = Main.secret
        val clientId = secret.appId
        val uri = "https://www.strava.com/oauth/authorize?"
        val state = afterLoginParams.fold("")(pars => "&state=" + URLEncoder.encode(pars, "UTF-8"))
        val action = uri + "client_id=" + clientId + "&response_type=code&redirect_uri=" + afterLogin + state + "&scope=read,activity:read_all,activity:write&approval_prompt=force"
        <h3>Work in progress, use at your own risk.</h3>
          <p>
            Strava activity editing (including uploading, merging and splitting)
          </p>
          <ul>
            <li>Merge activities</li>
            <li>Edit lap information</li>
            <li>Show activity map</li>
            <li>Split activities</li>
          </ul> :+ {
          if (clientId.nonEmpty) {
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

}
