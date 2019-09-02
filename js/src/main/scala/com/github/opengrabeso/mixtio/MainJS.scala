package com.github.opengrabeso.mixtio

import scala.scalajs.js
import js.annotation._
import JSFacade._
import org.scalajs.dom.document
import org.scalajs.dom.raw._

object MainJS {
  @JSExportTopLevel("jsAppName")
  def jsAppName(): String = appName

  @JSExportTopLevel("removeEvent")
  def removeEvent(time: String): Unit = {
    val tableLink = document.getElementById("link" + time)
    tableLink.innerHTML = ""
  }

  @JSExportTopLevel("selectOption")
  def selectOption(e: js.Array[String]): Unit = {
    val optionEl = document.getElementById(e(1))
    optionEl match {
      case tableOption: HTMLOptionElement =>
        // select appropriate option
        tableOption.value = e(0)

        // we need to update the table source, because it is used to create map popups
        // http://stackoverflow.com/a/40766724/16673
        val opts = tableOption.getElementsByTagName("option")
        for (i <- 0 until opts.length) {
          opts(i).removeAttribute("selected")
        }
        val checked = tableOption.querySelector("option:checked")
        if (checked != null) checked.setAttribute("selected", "selected")
      case _ =>
    }
  }
}
