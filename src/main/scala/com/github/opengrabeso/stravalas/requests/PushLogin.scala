package com.github.opengrabeso.stravalas
package requests

import java.net.URLEncoder
import javax.servlet.http.HttpServletResponse

import spark.{Request, Response}

import scala.util.Try

object PushLogin extends DefineRequest("/push-login") with ActivityRequestHandler {
  override def urlPrefix = "push-"

  // DRY: LogIn
  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val code = request.queryParams("code")
    val authResult = Try {
      Main.stravaAuth(code)
    }
    authResult.map { auth =>
      resp.cookie("authCode", code, 3600 * 24 * 30) // 30 days
      session.attribute("auth", auth)
      resp.redirect("/push-do")
    }.getOrElse {
      resp.cookie("authCode", "", 0) // delete the cookie
      resp.redirect("/push-start", HttpServletResponse.SC_MOVED_TEMPORARILY)
    }
    Nil
  }
}
