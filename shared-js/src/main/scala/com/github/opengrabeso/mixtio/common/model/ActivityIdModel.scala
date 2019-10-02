package com.github.opengrabeso.mixtio
package common.model

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

case class ActivityIdModel(id: FileId, digest: String, name: String, startTime: ZonedDateTime, endTime: ZonedDateTime, sportName: SportId, distance: Double)

object ActivityIdModel extends EnhancedRestDataCompanion[ActivityIdModel]

