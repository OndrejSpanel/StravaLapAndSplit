package com.github.opengrabeso.stravamat
package shared

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTimeZone, DateTime => ZonedDateTime}

object Util {

  implicit class ZonedDateTimeOps(val time: ZonedDateTime) extends AnyVal with Ordered[ZonedDateTimeOps] {
    override def compare(that: ZonedDateTimeOps): Int = time.compareTo(that.time)

    def toLog: String = {
      val format = DateTimeFormat.forPattern("dd/MM HH:mm:ss")
      format.print(time)
    }

    def toLogShort: String = {
      val format = DateTimeFormat.forPattern("HH:mm:ss")
      format.print(time)
    }

    def toFileName: String = {
      val format = DateTimeFormat.forPattern("YYYY-MM-dd-HH-mm")
      format.print(time)
    }
  }

  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = new Ordering[ZonedDateTime] {
    override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
  }

  implicit class MinMaxOptTraversable[T](val seq: Traversable[T]) extends AnyVal {
    def minOpt(implicit ev: Ordering[T]): Option[T] = if (seq.isEmpty) None else Some(seq.min)
    def maxOpt(implicit ev: Ordering[T]): Option[T] = if (seq.isEmpty) None else Some(seq.max)
  }

  def timeToUTC(dateTime: ZonedDateTime) = {
    dateTime.withZone(DateTimeZone.UTC)
  }

  def timeDifference(beg: ZonedDateTime, end: ZonedDateTime): Double = {
    (end.getMillis - beg.getMillis) * 0.001
  }

  def kiloCaloriesFromKilojoules(kj: Double): Int = (kj / 4184).toInt


  def humanReadableByteCount(bytes: Long): String = {
    val unit = 1024
    if (bytes < unit) return bytes + " B"
    val exp = (Math.log(bytes) / Math.log(unit)).toInt
    val pre = "kMGTPE".charAt(exp - 1)
    "%.1f %sB".format(bytes / Math.pow(unit, exp), pre)
  }

  implicit class HumanReadableByteCount(val bytes: Long) extends AnyVal {
    def toByteSize: String = humanReadableByteCount(bytes)
  }
}
