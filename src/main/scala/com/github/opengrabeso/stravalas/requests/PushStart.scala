package com.github.opengrabeso.stravalas
package requests

import java.net.URLEncoder
import javax.servlet.http.HttpServletResponse

import spark.{Request, Response}

import scala.util.Try

object PushStart extends DefineRequest("/push-start") with ActivityRequestHandler {
  private def retryLogin(resp: Response): Unit = {
    resp.cookie("authCode", "", 0) // delete the cookie
    resp.redirect("/", HttpServletResponse.SC_MOVED_TEMPORARILY)
    // TODO: perform the OAuth login, once done, report to the push application local server
    ???
    //loginHtml(request, resp)
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
        resp.cookie("authCode", code, 3600 * 24 * 30) // 30 days
        session.attribute("auth", auth)
        resp.redirect(s"http://localhost:$port/auth?user=${URLEncoder.encode(auth.userId, "UTF-8")}")
      }
    }.getOrElse {
      retryLogin(resp)
    }
    Nil
  }
}
