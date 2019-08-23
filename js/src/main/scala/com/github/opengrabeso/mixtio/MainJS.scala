package com.github.opengrabeso.mixtio

import scala.scalajs.js
import js.annotation._

import JSFacade._

object MainJS {
  @JSExportTopLevel("jsAppName")
  def jsAppName(): String = appName

  @JSExportTopLevel("actIdNameWrap")
  def actIdNameWrap(): String = {
    actIdName()
  }
}
