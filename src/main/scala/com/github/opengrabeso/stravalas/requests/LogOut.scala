package com.github.opengrabeso.stravalas
package requests
import spark.{Request, Response}

object LogOut extends DefineRequest{
  override def handle = Handle("/logout")

  def html(request: Request, resp: Response) = {
    resp.cookie("authCode", "", 0)
    resp.redirect("/")
    Nil
  }
}
