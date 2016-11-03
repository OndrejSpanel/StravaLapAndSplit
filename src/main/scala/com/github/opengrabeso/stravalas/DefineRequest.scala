package com.github.opengrabeso.stravalas

import spark.{Request, Response}

import scala.xml.NodeSeq

trait DefineRequest {
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

  def bodyFooter: NodeSeq = {
    <p></p>
    <div id="footer" style="background-color:#fa8;overflow:auto">
      <a href="http://labs.strava.com/" id="powered_by_strava" rel="nofollow">
        <img align="left" src="static/api_logo_pwrdBy_strava_horiz_white.png" style="max-height:46px"/>
      </a>
      <p>© 2016 <a href="https://github.com/OndrejSpanel">Ondřej Španěl</a></p>
      <div/>
    </div>
  }
}
