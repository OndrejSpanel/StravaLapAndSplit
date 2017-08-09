package com.github.opengrabeso.stravamat
package requests
import spark.{Request, Response}

object LogOut extends DefineRequest("/logout") {
  def html(request: Request, resp: Response) = {
    resp.cookie("authCode", "", 0)
    resp.redirect("/")
    Nil
  }
}
