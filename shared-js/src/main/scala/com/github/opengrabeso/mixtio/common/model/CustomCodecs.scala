package com.github.opengrabeso.mixtio.common.model

import java.time.ZonedDateTime
import com.avsystem.commons.serialization.GenCodec

object CustomCodecs {
  object ZonedDateTimeAU {
    def apply(string: String): ZonedDateTime = ZonedDateTime.parse(string)
    def unapply(dateTime: ZonedDateTime): Option[String] = Some(dateTime.toString)
  }
  implicit val zonedDateCodec: GenCodec[ZonedDateTime] = GenCodec.fromApplyUnapplyProvider[ZonedDateTime](ZonedDateTimeAU)
}

