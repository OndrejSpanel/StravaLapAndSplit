package com.github.opengrabeso.stravalas

import spark.{Request, Response}

import scala.xml.NodeSeq

sealed trait Method
object Method {
  case object Get extends Method
  case object Put extends Method
  case object Post extends Method
  case object Delete extends Method

}

case class Handle(value: String, method: Method = Method.Get)

trait DefineRequest {
  def handle: Handle

  def apply(request: Request, resp: Response): AnyRef = {
    val nodes = html(request, resp)
    if (nodes.nonEmpty) {
      val docType = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd" >"""
      docType + nodes.toString
    } else resp.raw
  }

  def html(request: Request, resp: Response): NodeSeq

  def cond(boolean: Boolean) (nodes: NodeSeq): NodeSeq = {
    if (boolean) nodes else Nil
  }

  def headPrefix: NodeSeq = {
    <meta charset="utf-8"/>
    <link rel="icon" href="static/favicon.ico"/>
  }

  def bodyHeader(authToken: String): NodeSeq = {
    <p>Athlete:
      <b>
        {Main.athlete(authToken)}
      </b>
    </p>
  }

  def bodyFooter: NodeSeq = {
    <p></p>
    <div id="footer" style="background-color:#fca;overflow:auto">
      <a href="http://labs.strava.com/" id="powered_by_strava" rel="nofollow">
        <img align="left" src="static/api_logo_pwrdBy_strava_horiz_white.png" style="max-height:46px"/>
      </a>
      <p style="color:#fff">© 2016 <a href="https://github.com/OndrejSpanel" style="color:inherit">Ondřej Španěl</a></p>
      <div/>
    </div>
  }
}
