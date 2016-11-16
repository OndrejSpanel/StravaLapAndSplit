package net.suunto3rdparty

import org.joda.time.format.ISODateTimeFormat
import org.scalatest.{FlatSpec, Matchers}

class SuuntoMergeTest extends FlatSpec with Matchers with SuuntoData {
  behavior of "SuuntoMerge"

  it should "load Quest file" in {
    val move = questMove

    move.isEmpty shouldBe false

    move.foreach { m =>
      val hr = m.streamGet[DataStreamHRWithDist]
      hr.isEmpty shouldBe false

      m.streamGet[DataStreamLap].isEmpty shouldBe false

      val t = ISODateTimeFormat.dateTimeNoMillis.parseDateTime("2016-10-21T06:46:57Z")
      m.startTime.contains(t)
      m.duration shouldBe 842.4
    }
  }

  it should "load GPS pod file" in {
    val move = gpsPodMove

    move.isFailure shouldBe false

    move.foreach { m =>
      val gps = m.streamGet[DataStreamGPS]
      gps.isEmpty shouldBe false

      val t = ISODateTimeFormat.dateTimeNoMillis.parseDateTime("2016-10-21T06:46:01Z")
      m.startTime.contains(t)
      m.duration shouldBe 4664.6


    }

  }

  it should "merge GPS + Quest files" in {
    for (hr <- questMove; gps <- gpsPodMove) {
      val m = gps.addStream(hr, hr.stream[DataStreamHRWithDist])
      m.isEmpty shouldBe false
      m.duration shouldBe 4664.6
      m.isAlmostEmpty(30) shouldBe false

    }
  }

}
