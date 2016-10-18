package com.github.opengrabeso.stravalas

import scala.xml.NodeSeq

trait HtmlPage {
  def apply(): NodeSeq
}

object IndexHtml extends HtmlPage {
  def apply() = {
    <h1>My header</h1>
    <p>My paragraph</p>
  }

}
