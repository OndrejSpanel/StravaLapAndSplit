package com.github.opengrabeso.mixtio
package rest

import java.time.ZonedDateTime

import com.avsystem.commons.serialization.{GenCodec, GenKeyCodec}
import common.model.CustomCodecs.ZonedDateTimeAU
import io.udash.rest._

trait EnhancedRestImplicits extends DefaultRestImplicits {
  implicit val zonedDateTimeCodec: GenCodec[ZonedDateTime] = GenCodec.fromApplyUnapplyProvider(ZonedDateTimeAU)
  implicit val zonedDateTimeKeyCodec: GenKeyCodec[ZonedDateTime] = GenKeyCodec.create(ZonedDateTime.parse,_.toString)
}

object EnhancedRestImplicits extends EnhancedRestImplicits
