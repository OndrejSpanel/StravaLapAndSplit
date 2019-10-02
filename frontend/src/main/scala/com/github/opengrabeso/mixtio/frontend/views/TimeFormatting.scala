package com.github.opengrabeso.mixtio.frontend.views

import java.time.ZonedDateTime

import scala.scalajs.js
import org.scalajs.dom.experimental.intl

trait TimeFormatting {
  def locale: String = {
    import org.scalajs.dom
    val firstLanguage = dom.window.navigator.asInstanceOf[js.Dynamic].languages.asInstanceOf[js.Array[String]].headOption
    firstLanguage.getOrElse(dom.window.navigator.language)
  }

  def formatDateTime(t: js.Date): String = {
    new intl.DateTimeFormat(
      locale,
      intl.DateTimeFormatOptions(
        year = "numeric",
        month = "numeric",
        day = "numeric",
        hour = "numeric",
        minute = "numeric"
      )
    ).format(t)
  }

  def formatTime(t: js.Date) = {
    new intl.DateTimeFormat(
      locale,
      intl.DateTimeFormatOptions(
        hour = "numeric",
        minute = "numeric",
      )
    ).format(t)
  }

  implicit class ZonedDateTimeOps(t: ZonedDateTime) {
    def toJSDate: js.Date = {
      val text = t.toString // (DateTimeFormatter.ISO_ZONED_DATE_TIME)
      new js.Date(js.Date.parse(text))
    }
  }


  def displayTimeRange(startTime: ZonedDateTime, endTime: ZonedDateTime): String = {
    s"${formatDateTime(startTime.toJSDate)}...${formatTime(endTime.toJSDate)}"
  }


}
