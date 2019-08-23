package com.github.opengrabeso.mixtio

import scala.scalajs.js
import js.annotation._

import JSFacade._

import org.scalajs.dom.document

object MainJS {
  @JSExportTopLevel("jsAppName")
  def jsAppName(): String = appName

  @JSExportTopLevel("actIdNameWrap")
  def actIdNameWrap(): String = {
    actIdName()
  }

  @JSExportTopLevel("removeEvent")
  def removeEvent(time: String) {
    val tableLink = document.getElementById("link" + time)
    tableLink.innerHTML = ""
  }

}
