package com.github.opengrabeso.mixtio
package frontend
package views.edit

import java.time.temporal.ChronoUnit

import java.time.ZonedDateTime
import common.model._
import io.udash.HasModelPropertyCreator

case class EditEvent(action: String, time: Int, km: Double, originalAction: String)

object EditEvent extends HasModelPropertyCreator[EditEvent] {
  def apply(startTime: ZonedDateTime, e: Event, dist: Double): EditEvent = {
    new EditEvent(
      e.defaultEvent, ChronoUnit.SECONDS.between(startTime, e.stamp).toInt, dist, e.originalEvent
    )
  }
}
