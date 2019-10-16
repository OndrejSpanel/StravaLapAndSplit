package com.github.opengrabeso.mixtio

import scala.scalajs.js
import js.annotation._
import ActivityJS._
import Download._
import UploadProgress._
import io.udash.wrappers.jquery.jQ
import org.scalajs.dom.{Element, document, window}
import org.scalajs.dom.raw._
import org.querki.jquery.{JQueryXHR, _}

import scala.collection.mutable

object MainJS {
  @JSExportTopLevel("getCookie")
  def getCookie(cookieName: String): String = {
    val allCookies = document.cookie
    val cookieValue = allCookies.split(";").map(_.trim).find(_.startsWith(cookieName + "=")).map{s =>
      s.drop(cookieName.length + 1)
    }
    cookieValue.orNull
  }

  @JSExportTopLevel("appMain") // called by Version as an entry point
  def appMain(): Unit = {
    jQ((jThis: Element) => {
      val appRoot = jQ("#application").get(0)
      if (appRoot.isEmpty) {
        println("Application root element not found! Check your index.html file!")
      } else {
        frontend.ApplicationContext.application.run(appRoot.get)
      }
    })
  }

  @JSExportTopLevel("initEvents")
  def initEvents() = {
    //console.log("initEvents " + events.toString());
    events.foreach(e => {
      if (e(0).lastIndexOf("split", 0) == 0) {
        addEvent(e)
      } else {
        removeEvent(e(1))
      }
      selectOption(e)
    })
    showEventButtons()
    onPartChecked()
  }

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

  def addEvent(e: js.Array[String]) = {
    //console.log("Add event " + e[1]);
    val tableLink = document.getElementById("link" + e(1))
    tableLink.innerHTML = splitLink(id, e)
  }

  def testPredicate(f: js.Array[String] => Boolean) = {
    var ret = false
    events.foreach(e => {
      if (f(e)) {
        ret = true
      }
    })
    ret
  }

  // is tests current event state (as displayed on the page)

  def isCheckedLap(e: js.Array[String]) = {
    e(0) == "lap"
  }
  // was test original event state

  def wasUserLap(e: js.Array[String]) = {
    e(4) == "lap"
  }

  def wasLongPause(e: js.Array[String]) = {
    e(4).lastIndexOf("long pause") == 0
  }

  def wasAnyPause(e: js.Array[String]) = {
    e(4) == "pause" || wasLongPause(e)
  }

  def wasSegment(e: js.Array[String]) = {
    e(4).lastIndexOf("segment") == 0 || e(4).lastIndexOf("private segment") == 0
  }

  def wasHill(e: js.Array[String]) = {
    e(4) == "elevation"
  }

  def showEventButtons() = {

    def showOrHide(name: String, func: js.Array[String] => Boolean) = {
      if (testPredicate(func)) {
        $("#" + name).show()
      } else {
        $("#" + name).hide()
      }
    }

    def enableOrDisable(name: String, func: js.Array[String] => Boolean) = {
      //showOrHide(name, func)
      $("#" + name).prop("disabled", !testPredicate(func))
    }
    enableOrDisable("isCheckedLap", isCheckedLap)
    showOrHide("wasUserLap", wasUserLap)
    showOrHide("wasLongPause", wasLongPause)
    showOrHide("wasAnyPause", wasAnyPause)
    showOrHide("wasSegment", wasSegment)
    showOrHide("wasHill", wasHill)
  }

  def lapsClearAll() = {
    events.foreach(e => {
      if (isCheckedLap(e)) {
        changeEvent("", e(1))
      }
    })
    onEventsChanged()
    showEventButtons()
  }

  def lapsSelectByPredicate(f: js.Array[String] => Boolean) = {
    events.foreach(e => {
      if (f(e)) {
        changeEvent("lap", e(1))
      }
    })
    onEventsChanged()
    showEventButtons()
  }

  def lapsSelectUser() = {
    lapsSelectByPredicate(wasUserLap)
  }

  def lapsSelectLongPauses() = {
    lapsSelectByPredicate(wasLongPause)
  }

  def lapsSelectAllPauses() = {
    lapsSelectByPredicate(wasAnyPause)
  }

  @JSExportTopLevel("onPartChecked")
  def onPartChecked() = {
    // count how many are checked
    // if none or very few, hide the uncheck button
    val parts = $("input:checkbox")
    val total = parts.length
    val checked = parts.filter(":checked").length
    if (checked > 1 && checked < total) {
      $("#merge_button").show()
    } else {
      $("#merge_button").hide()
    }
    if (checked > 0) {
      $("#div_process").show()
      $("#div_no_process").hide()
    } else {
      $("#div_process").hide()
      $("#div_no_process").show()
    }
  }

  @JSExportTopLevel("changeEvent")
  def changeEvent(newValue: String, itemTime: String) = {
    //console.log("changeEvent", newValue, itemTime)
    events.foreach(e => {
      if (e(1) == itemTime) {
        e(0) = newValue
        selectOption(e)
      }
    })
    events.foreach(e => {
      if (e(1) == itemTime && e(0).lastIndexOf("split", 0) == 0) {
        addEvent(e)
      } else {
        removeEvent(e(1))
      }
    })
    // without changing the active event first it is often not updated at all, no idea why
    events.foreach(e => {
      if (e(0).lastIndexOf("split", 0) == 0) {
        addEvent(e)
      }
    })
    // execute the callback
    onEventsChanged()
    onPartChecked()
    showEventButtons()
  }

  @JSExportTopLevel("submitProcess")
  def submitProcess() = {
    document.getElementById("uploads_table").asInstanceOf[HTMLElement].style.display = "block"
    val form = $("#activity_form")
    $.ajax(new JQueryAjaxSettingsBuilder(Map (
      "type" -> form.attr("method"),
      "url" -> form.attr("action"),
      "data" -> new FormData(form(0).asInstanceOf[HTMLFormElement]),
      "contentType" -> false,
      "cache" -> false,
      "processData" -> false,
      "success" -> ({(response: Any, _: Any, _: Any) =>
        showResults()
      }: js.Function3[Any, Any, Any, Unit])
    )))
  }

  @JSExportTopLevel("submitEdit")
  def submitEdit() = {
    //document.getElementById("upload_button").style.display = "none";
    document.getElementById("uploads_table").asInstanceOf[HTMLElement].style.display = "block"
    val form = $("#activity_form")
    $.asInstanceOf[js.Dynamic].ajax(js.Dynamic.literal(
      `type` = form.attr("method"),
      url = "edit-activities",
      data = new FormData(form(0).asInstanceOf[HTMLFormElement]),
      contentType = false,
      cache = false,
      processData = false,
      success = {(response: XMLHttpRequest, _: Any, _: Any) =>
        val idElem = $(response).find("id")
        if (idElem.length > 0) {
          window.asInstanceOf[js.Dynamic].location = "edit-activity?id=" + idElem.first().text().trim()
        }
      }
    ))
  }

  @JSExportTopLevel("submitDownload")
  def submitDownload() = {
    val form = $("#activity_form")
    val ajax = new XMLHttpRequest()
    ajax.open("POST", "/download", true)
    ajax.responseType = "blob"
    ajax.onload = { e =>
      download(e.target.asInstanceOf[XMLHttpRequest].response, ajax.getResponseHeader("Content-Disposition"), ajax.getResponseHeader("content-type"))
    }
    ajax.send(new FormData(form(0).asInstanceOf[HTMLFormElement]))
  }

  def selectMapEvent(eventId: String) = {
    map.fire("popup", js.Dynamic.literal(
      feature = eventId
    ))
  }

}
