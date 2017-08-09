package com.github.opengrabeso.stravamat
package requests

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

}