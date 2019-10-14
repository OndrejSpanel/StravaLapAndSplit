package com.github.opengrabeso.mixtio
package frontend.model

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.github.opengrabeso.mixtio.common.model.Event
import io.udash.HasModelPropertyCreator

case class EditEvent(selected: Boolean, action: String, event: Event, time: Int, dist: Double) {
  def boundary: Boolean = action.startsWith("split")
  /// selected and selectable (selected can often be set for events which are not selectable at all)
  def processed: Boolean = {
    selected && boundary
  }
}

object EditEvent extends HasModelPropertyCreator[EditEvent] {
  def apply(startTime: ZonedDateTime, e: Event, dist: Double, selected: Boolean): EditEvent = {
    new EditEvent(
      selected, e.defaultEvent, e, ChronoUnit.SECONDS.between(startTime, e.stamp).toInt, dist
    )
  }
}
