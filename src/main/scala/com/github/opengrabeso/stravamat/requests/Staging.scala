package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}
import DateTimeOps._
import org.joda.time.{DateTime => ZonedDateTime, Seconds}
import net.suunto3rdparty.Settings

object Staging extends SelectActivity("/staging") {
  override def title = "select activities to process"

  override def sources(before: ZonedDateTime) = {
    <div>
      <h2>Data sources</h2>
      <a href="loadFromStrava">Load from Strava ...</a>
      <a href="getFiles">Upload files...</a>
      <hr/>
    </div>
  }

}