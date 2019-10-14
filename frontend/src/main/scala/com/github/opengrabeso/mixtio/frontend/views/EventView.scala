package com.github.opengrabeso.mixtio
package frontend.views

import common.Formatting
import common.model._
import frontend.model._
import org.scalajs.dom._
import scalatags.JsDom.all._

object EventView {
  implicit class Text(s: String) {
    def text = span(s).render
  }

  def segmentTitle(kind: String, e: SegmentTitle): Element = {
    val segPrefix = if (e.isPrivate) "private segment " else "segment "
    val segmentName = Formatting.shortNameString(e.name, 32 - segPrefix.length - kind.length)
    if (e.segmentId != 0) {
      span(
        (kind + segPrefix).capitalize,
        a(
          title := e.name,
          href := s"https://www.strava.com/segments/${e.segmentId}",
          segmentName
        )
      ).render
    } else {
      (kind + segPrefix + segmentName).capitalize.text
    }
  }

  def eventDescription(e: EditEvent): Element = {
    e.event match {
      case e: PauseEvent =>
        s"Pause ${Events.niceDuration(e.duration)}".text
      case e: PauseEndEvent =>
        "Pause end".text
      case e: LapEvent =>
        "Lap".text
      case e: EndEvent =>
        "End".text
      case e: BegEvent =>
        b("Start").render
      case e: SplitEvent =>
        "Split".text
      case e: StartSegEvent =>
        segmentTitle("", e)
      case e: EndSegEvent =>
        segmentTitle("end ", e)
      case e: ElevationEvent =>
        Formatting.shortNameString("Elevation " + e.elev.toInt + " m").text
    }

  }

}
