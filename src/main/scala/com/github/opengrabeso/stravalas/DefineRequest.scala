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
}
