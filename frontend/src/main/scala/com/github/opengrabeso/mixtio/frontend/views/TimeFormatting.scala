package com.github.opengrabeso.mixtio.frontend.views

import java.time.{ZoneOffset, ZonedDateTime}

import scala.scalajs.js
import org.scalajs.dom.experimental.intl

trait TimeFormatting {
  def locale: String = {
    import org.scalajs.dom
    val firstLanguage = dom.window.navigator.asInstanceOf[js.Dynamic].languages.asInstanceOf[js.Array[String]].headOption
    firstLanguage.getOrElse(dom.window.navigator.language)
  }

  def formatDateTime(t: js.Date): String = {
    try {
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
    } catch {
      case _: Exception =>
        s"Invalid time"
    }
  }

  def formatTime(t: js.Date) = {
    try {
      new intl.DateTimeFormat(
        locale,
        intl.DateTimeFormatOptions(
          hour = "numeric",
          minute = "numeric",
        )
      ).format(t)
    } catch {
      case _: Exception =>
        s"Invalid time"
    }
  }

  def formatTimeHMS(t: js.Date) = {
    try {
      new intl.DateTimeFormat(
        locale,
        intl.DateTimeFormatOptions(
          hour = "numeric",
          minute = "numeric",
          second = "numeric",
        )
      ).format(t)
    } catch {
      case _: Exception =>
        s"Invalid time"
    }
  }

  implicit class ZonedDateTimeOps(t: ZonedDateTime) {
    def toJSDate: js.Date = {
      // without "withZoneSameInstant" the resulting time contained strange [SYSTEM] zone suffix
      val text = t.withZoneSameInstant(ZoneOffset.UTC).toString // (DateTimeFormatter.ISO_ZONED_DATE_TIME)
      new js.Date(js.Date.parse(text))
    }
  }

  def displayTimeRange(startTime: ZonedDateTime, endTime: ZonedDateTime): String = {
    s"${formatDateTime(startTime.toJSDate)}...${formatTime(endTime.toJSDate)}"
  }
}

object TimeFormatting extends TimeFormatting