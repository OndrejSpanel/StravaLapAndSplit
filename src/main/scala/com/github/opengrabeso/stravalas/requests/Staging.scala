package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}
import DateTimeOps._
import org.joda.time.{DateTime => ZonedDateTime, Seconds}
import net.suunto3rdparty.Settings

object Staging extends SelectActivity("/staging") {
}