package com.github.opengrabeso.stravamat

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object DateTimeOps {
  implicit class ZonedDateTimeOps(private val time: DateTime) extends AnyVal with Ordered[ZonedDateTimeOps] {
    override def compare(that: ZonedDateTimeOps): Int = time.compareTo(that.time)

    def toLog: String = {
      val format = DateTimeFormat.forPattern("dd/MM HH:mm:ss")
      format.print(time)
    }

    def toLogShort: String = {
      val format = DateTimeFormat.forPattern("HH:mm:ss")
      format.print(time)
    }

  }

  implicit def zonedDateTimeOrdering: Ordering[DateTime] = new Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime): Int = x.compareTo(y)
  }


}
