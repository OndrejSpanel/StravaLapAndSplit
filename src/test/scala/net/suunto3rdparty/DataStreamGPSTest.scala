package net.suunto3rdparty

import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.SortedMap
import Util._

class DataStreamGPSTest extends FlatSpec with Matchers with SuuntoData {
  behavior of "DataStreamGPS"


  it should "Handle missing samples correctly" in {
    val move = gpsPodMove
    for (m <- move) {
      val gps = m.stream[DataStreamGPS]
      val dist = m.stream[DataStreamDist]

      val t = gps.startTime.get
      // sample 271 missing in the GPS stream
      val time = 270 - 1 // <Time>270</Time>

      def relTime(t: DateTime, r: Int) = t.withDurationAdded(r, 1000)

      // verify the test data demonstrate the problem
      gps.stream.get(relTime(t, time + 0)) shouldNot be(None)
      gps.stream.get(relTime(t, time + 1)) should be(None)
      gps.stream.get(relTime(t, time + 2)) shouldNot be(None)

      // verify even the dist stream is still missing the data
      val distStream = SortedMap(gps.distStream:_*)
      distStream.get(relTime(t, time + 0)) shouldNot be(None)
      distStream.get(relTime(t, time + 1)) should be(None)
      distStream.get(relTime(t, time + 2)) shouldNot be(None)

    }

  }
}
