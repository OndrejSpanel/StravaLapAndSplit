package com.github.opengrabeso.mixtio
package common.model

import java.time.ZonedDateTime

import com.avsystem.commons.serialization.GenCodec
import rest.EnhancedRestDataCompanion

object CustomCodecs {
  object ZonedDateTimeAU {
    def apply(string: String): ZonedDateTime = ZonedDateTime.parse(string)
    def unapply(dateTime: ZonedDateTime): Option[String] = Some(dateTime.toString)
  }
  implicit val zonedDateCodec: GenCodec[ZonedDateTime] = GenCodec.fromApplyUnapplyProvider[ZonedDateTime](ZonedDateTimeAU)
}

case class ActivityIdModel(id: FileId, digest: String, name: String, startTime: ZonedDateTime, endTime: ZonedDateTime, sportName: SportId, distance: Double)

object ActivityIdModel extends EnhancedRestDataCompanion[ActivityIdModel]

