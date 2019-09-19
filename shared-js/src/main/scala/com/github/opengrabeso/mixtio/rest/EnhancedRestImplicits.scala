package com.github.opengrabeso.mixtio.rest

import java.time.ZonedDateTime

import com.avsystem.commons.rpc._
import io.udash.rest._
import io.udash.rest.raw.JsonValue

trait EnhancedRestImplicits extends DefaultRestImplicits {
  implicit val zonedDateTimeJsonAsRawReal: AsRawReal[JsonValue, ZonedDateTime] =
    AsRawReal.create(
      { real =>
        JsonValue(real.toString)
      },{ raw =>
        ZonedDateTime.parse(raw.value)
      }
    )
}

object EnhancedRestImplicits extends EnhancedRestImplicits
