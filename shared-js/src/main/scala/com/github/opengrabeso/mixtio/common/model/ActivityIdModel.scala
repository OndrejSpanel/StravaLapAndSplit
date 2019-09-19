package com.github.opengrabeso.mixtio
package common.model

import java.time.ZonedDateTime

import com.avsystem.commons.serialization.HasGenCodec
import io.udash.rest.RestDataCompanion
import io.udash.rest.openapi.{RestSchema, RestStructure}


case class ActivityIdModel(id: String, digest: String, name: String, startTime: String, endTime: String, sportName: String, distance: Double)

object ActivityIdModel extends RestDataCompanion[ActivityIdModel] {
  object ZonedDateTimeAU {
    def apply(string: String): ZonedDateTime = ZonedDateTime.parse(string)
    def unapply(dateTime: ZonedDateTime): Option[String] = Some(dateTime.toString)
  }
  implicit val zonedDateTimeSchema: RestSchema[ZonedDateTime] = RestStructure.fromApplyUnapplyProvider[ZonedDateTime](ZonedDateTimeAU).standaloneSchema
  implicit val activityIdModelSchema: RestSchema[ActivityIdModel] = RestStructure.materialize[ActivityIdModel].standaloneSchema
}
