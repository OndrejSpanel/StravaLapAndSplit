package com.github.opengrabeso.mixtio

import scala.scalajs.js
import js.annotation._
import JSFacade._
import org.scalajs.dom.document
import org.scalajs.dom.raw._

import scala.collection.mutable

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

  implicit class ToFixedOp(x: Double) {
    def toFixed(decimal: Int): String = f"$x%.2f"
  }

  @JSExportTopLevel("splitLink")
  def splitLink(id: Any, event: js.Array[String]) = {
    val time = event(1)
    val selectCheckbox = "<input type=\"checkbox\" name=\"process_time=" + time + "\"} checked=true onchange=\"onPartChecked(this)\"></input>"
    val splitPrefix = "split"
    var nextSplit: js.Array[String] = null
    events.foreach(e => {
      if (e(0).lastIndexOf(splitPrefix, 0) == 0 && e(1) > time && nextSplit == null) {
        nextSplit = e
      }
    })
    if (nextSplit == null) nextSplit = events(events.length - 1)
    var description = "???"
    if (nextSplit != null) {
      val km = (nextSplit(2).toDouble - event(2).toDouble) / 1000
      val duration = nextSplit(1).toDouble - event(1).toDouble
      var kmH = true
      var minKm = true
      val sport = event(0).substring(splitPrefix.length)
      if (sport == "Run") kmH = false
      if (sport == "Ride") minKm = false
      val elements = mutable.ArrayBuffer(km.toFixed(1) + " km")
      if (minKm) {
        val paceSecKm = if (km > 0) duration / km else 0
        val paceMinKm = paceSecKm / 60
        elements += paceMinKm.toFixed(2) + " min/km"
      }
      if (kmH) {
        val speedKmH = if (duration > 0) km * 3600 / duration else 0
        elements += speedKmH.toFixed(1) + " km/h"
      }
      description = elements.mkString(" / ")
    }
    selectCheckbox + description
  }

}
