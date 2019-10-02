package com.github.opengrabeso.mixtio
package common.model

import com.avsystem.commons.misc.{AbstractValueEnum, AbstractValueEnumCompanion, EnumCtx}

final class SportId(implicit enumCtx: EnumCtx) extends AbstractValueEnum
object SportId extends AbstractValueEnumCompanion[SportId] {
  // https://strava.github.io/api/v3/uploads/
  //   ride, run, swim, workout, hike, walk, nordicski, alpineski, backcountryski, iceskate, inlineskate, kitesurf,
  //   rollerski, windsurf, workout, snowboard, snowshoe, ebikeride, virtualride

  // order by priority, roughly fastest to slowest (prefer faster sport does less harm on segments)
  // Workout (as Unknown) is the last option
  final val Ride, Run, Hike, Walk, Swim, NordicSki, AlpineSki, IceSkate, InlineSkate, KiteSurf,
    RollerSki, WindSurf, Canoeing, Kayaking, Rowing, Surfing, Snowboard, Snowshoe, EbikeRide, VirtualRide, Workout: Value = new SportId
}
