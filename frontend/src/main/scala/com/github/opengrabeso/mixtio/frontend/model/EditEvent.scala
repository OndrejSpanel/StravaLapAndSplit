package com.github.opengrabeso.mixtio
package frontend.model

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.github.opengrabeso.mixtio.common.model.Event
import io.udash.HasModelPropertyCreator

case class EditEvent(action: String, event: Event, time: Int, dist: Double) {
  def boundary: Boolean = action.startsWith("split")
}

object EditEvent extends HasModelPropertyCreator[EditEvent] {
  def apply(startTime: ZonedDateTime, e: Event, dist: Double): EditEvent = {
    new EditEvent(
      e.defaultEvent, e, ChronoUnit.SECONDS.between(startTime, e.stamp).toInt, dist
    )
  }
}
