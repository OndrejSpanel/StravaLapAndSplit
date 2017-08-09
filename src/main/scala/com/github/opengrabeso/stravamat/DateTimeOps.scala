package com.github.opengrabeso.stravamat

import org.joda.time.DateTime

object DateTimeOps {
  implicit class ZonedDateTimeOps(private val time: DateTime) extends AnyVal with Ordered[ZonedDateTimeOps] {
    override def compare(that: ZonedDateTimeOps): Int = time.compareTo(that.time)

  }

  implicit def zonedDateTimeOrdering: Ordering[DateTime] = new Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime): Int = x.compareTo(y)
  }
}
