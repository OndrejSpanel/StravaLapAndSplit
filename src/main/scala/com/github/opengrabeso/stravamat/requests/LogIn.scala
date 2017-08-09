package com.github.opengrabeso.stravamat
package requests

import javax.servlet.http.HttpServletResponse

import spark.{Request, Response}

import scala.util.Try

object LogIn extends DefineRequest("/login") {
  override def html(request: Request, resp: Response) = {
    val session = request.session()
    val code = request.queryParams("code")
    val authResult = Try {
      Main.stravaAuth(code)
    }
    authResult.map { auth =>
      resp.cookie("authCode", code, 3600 * 24 * 30) // 30 days
      session.attribute("auth", auth)
      resp.redirect("/selectActivity")
    }.getOrElse {
      resp.cookie("authCode", "", 0) // delete the cookie
      resp.redirect("/", HttpServletResponse.SC_MOVED_TEMPORARILY)
    }
    Nil
  }
}
