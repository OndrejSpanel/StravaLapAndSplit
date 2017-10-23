package com.github.opengrabeso.stravamat.mapbox

import org.scalactic.TolerantNumerics
import org.scalatest.FunSuite

class GetElevationTest extends FunSuite {

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.1)

  test("test Mount Everest") {
    val summit = GetElevation(86.925313, 27.988730)
    assert(summit === 8742.8)

    val summitRange = new GetElevation.TileCache().possibleRange(86.925313, 27.988730)
    assert(summitRange._1 === 8742.6)
    assert(summitRange._2 === 8743.0)

  }

}
