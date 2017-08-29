package com.github.opengrabeso.stravamat
package requests

import Main._
import org.joda.time.{DateTime => ZonedDateTime}

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

  override def ignoreBefore(stravaActivities: Seq[ActivityId]) = {
    new ZonedDateTime() minusMonths 24
  }

}