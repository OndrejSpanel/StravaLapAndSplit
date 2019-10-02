package com.github.opengrabeso.mixtio
package requests

import common.model._
import java.time.ZonedDateTime

object Staging extends SelectActivity("/staging") {
  override def title = "select activities to process"

  override def sources(before: ZonedDateTime) = {
    <div>
      <h2>Data sources</h2>
      <a href="loadFromStrava">Load from Strava ...</a>
      <a href="getFiles">Upload files...</a>
      <a href="settings">Settings...</a>
      <hr/>
    </div>
  }

  override def ignoreBefore(stravaActivities: Seq[ActivityId]) = {
    ZonedDateTime.now() minusMonths 24
  }

}