package com.github.opengrabeso.mixtio.rest

import java.time.ZonedDateTime

import com.avsystem.commons.rpc._
import io.udash.rest._
import io.udash.rest.raw._

trait EnhancedRestImplicits extends DefaultRestImplicits {
  implicit val zonedDateTimeJsonAsRawReal: AsRawReal[JsonValue, ZonedDateTime] =
    AsRawReal.create(
      { real =>
        JsonValue(real.toString)
      },{ raw =>
        ZonedDateTime.parse(raw.value)
      }
    )
  implicit val zonedDateTimePlainAsRawReal: AsRawReal[PlainValue, ZonedDateTime] =
    AsRawReal.create(
      { real =>
        PlainValue(real.toString)
      },{ raw =>
        ZonedDateTime.parse(raw.value)
      }
    )
}

object EnhancedRestImplicits extends EnhancedRestImplicits
