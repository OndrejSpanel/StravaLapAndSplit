package com.github.opengrabeso.mixtio
package common.model

import java.time.ZonedDateTime

import com.avsystem.commons.meta.MacroInstances
import com.avsystem.commons.serialization.GenCodec
import io.udash.rest.{CodecWithStructure, DefaultRestImplicits}
import io.udash.rest.openapi.{RestSchema, RestStructure}

object CustomCodecs {
  object ZonedDateTimeAU {
    def apply(string: String): ZonedDateTime = ZonedDateTime.parse(string)
    def unapply(dateTime: ZonedDateTime): Option[String] = Some(dateTime.toString)
  }
  implicit val zonedDateCodec: GenCodec[ZonedDateTime] = GenCodec.fromApplyUnapplyProvider[ZonedDateTime](ZonedDateTimeAU)
}

case class ActivityIdModel(id: String, digest: String, name: String, startTime: ZonedDateTime, endTime: ZonedDateTime, sportName: String, distance: Double)

abstract class EnhancedRestDataCompanion {
  type T = ActivityIdModel
  implicit val zonedDateCodec = CustomCodecs.zonedDateCodec
  implicit val instances: MacroInstances[DefaultRestImplicits, CodecWithStructure[T]] = implicitly[MacroInstances[DefaultRestImplicits, CodecWithStructure[T]]]
  implicit lazy val codec: GenCodec[T] = instances(DefaultRestImplicits, this).codec
  implicit lazy val restStructure: RestStructure[T] = instances(DefaultRestImplicits, this).structure
  implicit lazy val restSchema: RestSchema[T] = RestSchema.lazySchema(restStructure.standaloneSchema)
}

object ActivityIdModel extends EnhancedRestDataCompanion

