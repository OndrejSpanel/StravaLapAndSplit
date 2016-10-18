package com.github.opengrabeso.stravalas

import spark.Request

import scala.xml.NodeSeq

/**
  * Created by Ondra on 18.10.2016.
  */
trait HtmlPage {
  def apply(request: Request): String = {
    val docType = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd" >"""
    docType + html(request).toString
  }

  def html(request: Request): NodeSeq
}
