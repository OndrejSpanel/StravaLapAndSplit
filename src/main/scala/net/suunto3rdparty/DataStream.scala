package net.suunto3rdparty

import org.joda.time.{Period, ReadablePeriod, Seconds, DateTime => ZonedDateTime}

import scala.collection.immutable.{Queue, SortedMap}
import Util._

import scala.annotation.tailrec

case class GPSPoint(latitude: Double, longitude: Double, elevation: Option[Int])
case class HRPoint(hr: Int, dist: Double)

object DataStream {
  def distanceIsAlmostEmpty(begDist: Double, endDist: Double, begTime: ZonedDateTime, endTime: ZonedDateTime): Boolean = {
    val dist = endDist - begDist
    val duration = timeDifference(begTime, endTime)
    val maxSpeed = 0.1
    dist < duration * maxSpeed
  }

  def mapStreamValues[Item, T](stream: SortedMap[ZonedDateTime, Item], f: Item => T): SortedMap[ZonedDateTime, T] = {
    // note: not very fast, rebuilds the SortedMap
    // mapValues however creates a copy which is not serializable
    stream.transform((_, v) => f(v))
  }
}
sealed abstract class DataStream[Item] {

  def typeToLog: String
  def streamType: Class[_ <: DataStream[_]] = this.getClass

  type DataItem = Item
  type DataMap = SortedMap[ZonedDateTime, DataItem]

  def stream: DataMap

  assert(stream.isEmpty || stream.head._1 <= stream.last._1)

  def mapStreamValues[T](f: Item => T): SortedMap[ZonedDateTime, T] = DataStream.mapStreamValues(stream, f)

  def pickData(data: DataMap): DataStream[Item]

  val startTime: Option[ZonedDateTime] = stream.headOption.map(_._1)
  val endTime: Option[ZonedDateTime] = stream.lastOption.map(_._1)

  def inTimeRange(b: ZonedDateTime, e: ZonedDateTime): Boolean = {
    startTime.forall(_ >= b) && endTime.forall(_ <= e)
  }

  // should be discarded
  def isAlmostEmpty: Boolean

  // must not be discarded
  def isNeeded: Boolean

  def span(time: ZonedDateTime): (DataStream[Item], DataStream[Item]) = {
    val (take, left) = stream.span(_._1 < time)
    (pickData(take), pickData(left))
  }

  def slice(timeBeg: ZonedDateTime, timeEnd: ZonedDateTime): DataStream[Item] = {

    val subData = stream.filter(i => i._1 >= timeBeg && i._1 <= timeEnd)

    pickData(subData)
  }

  def timeOffset(bestOffset: Int): DataStream[Item] = {
    val adjusted = stream.map{
      case (k,v) =>
        k.plus(bestOffset*1000) -> v
    }
    pickData(adjusted)
  }

  def toLog = s"$typeToLog: ${startTime.map(_.toLog).getOrElse("")} .. ${endTime.map(_.toLogShort).getOrElse("")}"

  override def toString = toLog

}

object DataStreamGPS {
  private final case class GPSRect(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double) {
    def this(item: GPSPoint) = {
      this(item.latitude, item.latitude, item.longitude, item.longitude)
    }

    def merge(that: GPSPoint) = {
      copy(
        latMin = that.latitude min latMin, latMax = that.latitude max latMax,
        lonMin = that.longitude min lonMin, lonMax = that.longitude max lonMax
      )
    }

    // diagonal size of the rectangle
    def size: Double = GPS.distance(latMin, lonMin, latMax, lonMax)
  }

  def rectAlmostEmpty(rect: GPSRect, timeBeg: ZonedDateTime, timeEnd: ZonedDateTime): Boolean = {
    val d = rect.size
    val duration = timeDifference(timeBeg, timeEnd).abs
    val maxSpeed = 0.2
    d <= (maxSpeed * duration min 100)
  }


  private type DistStream  = Seq[(ZonedDateTime, Double)]

  /**
    * Experiments have shown smoothingInterval = 60 gives most accurate results.
    * Perhaps the same smoothing interval is used in the Quest itself?
    */
  private val smoothingInterval = 60


  private type DistList = List[(ZonedDateTime, Double)]
  /**
    * GPS records sometimes miss one sample, the missing sample is added the a neighboring sample, like:
    * 2016-04-13T09:47:01Z	4.9468178241
    * 2016-04-13T09:47:02Z	10.5000627924
    * 2016-04-13T09:47:04Z	5.2888359044
    */
  /**
    * Currently unused, previous example was wrong, it was result of bad handling of time deltas between GPS samples.
    * */
  def fixSpeed(input: DistList): DistList = {
    def fixSpeedRecurse(input: DistList, done: DistList): DistList = {
      if (input.isEmpty) done
      else {
        if (input.tail.isEmpty) fixSpeedRecurse(input.tail, input.head +: done)
        else {
          val item0 = input.head
          val item1 = input.tail.head
          val duration = Seconds.secondsBetween(input.head._1, input.tail.head._1).getSeconds
          if (duration>1) {
            // missing sample
            val missingCount = duration - 1
            val value0 = item0._2
            val value1 = item1._2
            if (value0 >= value1) {
              // 0 large, 1 missing, 2 small
              val fixed0 = item0.copy(_2 = value0 / duration)
              val addMissing = for (s <- (1 to missingCount.toInt).toList) yield fixed0.copy(_1 = item0._1.plusSeconds(s))
              fixSpeedRecurse(input.tail, addMissing ++ (fixed0 +: done))
            } else {
              // 0 small, 1 missing, 2 large
              val fixed2 = item1.copy(_2 = value0 / duration)
              val addMissing = for (s <- (1 to missingCount.toInt).toList) yield fixed2.copy(_1 = item0._1.plusSeconds(s))
              fixSpeedRecurse(input.tail.tail, (fixed2 +: addMissing) ++ (item0 +: done))
            }
          } else {
            fixSpeedRecurse(input.tail, input.head +: done)
          }

        }
      }
    }

    fixSpeedRecurse(input, Nil).reverse
  }

  private def smoothSpeed(rawInput: DistStream, durationSec: Int): DistStream = {
    val input = rawInput.toList

    class History(duration: Int) {
      var tSum = 0
      var dSum = 0.0
      var samples = Queue[(Int, Double)]()

      def addSample(t: Int, d: Double): Unit = {
        tSum += t
        dSum += d
        samples = samples :+ (t, d)
        while (tSum > duration) {
          val pop = samples.head
          samples = samples.tail
          tSum -= pop._1
          dSum -= pop._2
        }
      }

      def avgSpeed: Double = if (tSum > 0) dSum / tSum else 0
    }

    val distTimes = input.map(_._1)
    // duplicate first sample to provide a zero time for it

    val timeDeltas = (distTimes.head +: distTimes zip distTimes).map(tt => Seconds.secondsBetween(tt._1, tt._2).getSeconds)

    val distDeltas = timeDeltas zip input.map(_._2)

    val h = new History(durationSec)

    val speeds = for ((td, dd) <- distDeltas) yield {
      h.addSample(td, dd)
      h.avgSpeed
    }

    //val speeds = for ((td, dd) <- distDeltas) yield if (td > 0) dd / td else 0

    distTimes zip speeds
  }

  private def pairToDist(ab: (GPSPoint, GPSPoint)) = {
    val (a, b) = ab
    val rect = new GPSRect(a).merge(b)
    rect.size
  }

  def distStreamFromGPSList(gps: Seq[GPSPoint]): Seq[Double] = {
    val gpsDistances = (gps zip gps.tail).map(pairToDist)
    gpsDistances
  }

  def distStreamFromGPS(gps: SortedMap[ZonedDateTime, GPSPoint]): DistStream = {
    val gpsKeys = gps.keys.toSeq // toSeq needed to preserve order
    val gpsValues = gps.values.toSeq
    val gpsPairs = gpsKeys.drop(1) zip (gpsValues zip gpsValues.drop(1))
    val gpsDistances = gpsPairs.map { case (t, p) => t -> pairToDist(p) }
    (gpsKeys.head -> 0.0) +: gpsDistances
  }

  def routeStreamFromDistStream(distDeltas: DistStream): DistStream = {
    val route = distDeltas.scanLeft(0d) { case (sum, (_, d)) => sum + d }
    // scanLeft adds initial value as a first element - use tail to drop it
    distDeltas.map(_._1) zip route.tail
  }

  def routeStreamFromSpeedStream(distDeltas: DistStream): DistStream = {
    if (distDeltas.isEmpty) Seq()
    else {
      assert(distDeltas.head._2 == 0)
      val route = distDeltas.tail.scanLeft(distDeltas.head) { case ((tSum, dSum), (t, d)) =>
        val dt = Seconds.secondsBetween(tSum, t).getSeconds
        t -> (dSum + d * dt)
      }
      route
    }
  }

  def computeSpeedStream(dist: DistStream, smoothing: Int = smoothingInterval): DistStream = {
    val smoothedSpeed = smoothSpeed(dist, smoothing)
    smoothedSpeed
  }

  /**
    * @return median, average, max
    * */
  def speedStats(speedStream: DistStream): (Double, Double, Double) = {

    def median(s: Seq[Double])  = {
      val (lower, upper) = s.sorted.splitAt(s.size / 2)
      if (s.size % 2 == 0) (lower.last + upper.head) / 2.0 else upper.head
    }

    val toKmh = 3.6
    val speeds = speedStream.map(_._2 * toKmh)

    val max = speeds.max
    val min = speeds.min

    val num_bins = 10

    val histogram = speeds
      .map(x => (((x - min) / (max - min)) * num_bins).floor.toInt)
      .groupBy(identity)
      .map(x => x._1 -> x._2.size)
      .toSeq
      .sortBy(_._1)
      .map(_._2)

    def percentile(percent: Int) = {
      val countUnder = (percent * 0.01 * speeds.size).toInt

      def percentileRecurse(countLeft: Int, histLeft: Seq[Int], ret: Int): Int = {
        if (histLeft.isEmpty || histLeft.head >= countLeft) ret
        else percentileRecurse(countLeft - histLeft.head, histLeft.tail, ret + 1)
      }
      val slot = percentileRecurse(countUnder, histogram, 0)
      slot.toDouble / num_bins * (max - min) + min
    }


    val med = median(speeds)

    val fast = percentile(80)
    // TODO:
    (med, fast, max)
  }


}

case class DataStreamGPS(override val stream: SortedMap[ZonedDateTime, GPSPoint]) extends DataStream[GPSPoint] {

  import DataStreamGPS._

  def typeToLog: String = "GPS"

  override def pickData(data: DataMap) = new DataStreamGPS(data)

  override def isAlmostEmpty: Boolean = {
    if (stream.isEmpty) true
    else {
      val lat = stream.values.map(_.latitude)
      val lon = stream.values.map(_.longitude)
      // http://www.movable-type.co.uk/scripts/latlong.html
      val rect = GPSRect(lat.min, lat.max, lon.min, lon.max)

      rectAlmostEmpty(rect, stream.head._1, stream.last._1)
    }
  }

  override def isNeeded = false
  // drop beginning and end with no activity
  private type ValueList = List[(ZonedDateTime, GPSPoint)]

  def dropAlmostEmpty: Option[(ZonedDateTime, ZonedDateTime)] = {
    if (stream.nonEmpty) {

      @tailrec
      def detectEmptyPrefix(begTime: ZonedDateTime, rect: GPSRect, stream: ValueList, ret: Option[(ZonedDateTime, GPSRect)]): Option[(ZonedDateTime, GPSRect)] = {
        stream match {
          case Nil => ret
          case head :: tail =>
            val newRect = rect merge head._2
            val newRet = if (rectAlmostEmpty(rect, begTime, head._1)) Some((head._1, newRect)) else ret
            detectEmptyPrefix(begTime, newRect, tail, newRet)
        }
      }

      def dropEmptyPrefix(stream: ValueList, timeOffset: ReadablePeriod, compare: (ZonedDateTime, ZonedDateTime) => Boolean): (ZonedDateTime, ZonedDateTime) = {
        val prefixTime = detectEmptyPrefix(stream.head._1, new GPSRect(stream.head._2), stream, None)
        prefixTime.map { case (prefTime, prefRect) =>
          // trace back the prefix rectangle size
          val backDistance = prefRect.size

          val prefixRaw = stream.takeWhile(t => compare(t._1, prefTime))

          val gpsDist = DataStreamGPS.distStreamFromGPSList(prefixRaw.map(_._2)).reverse

          def trackBackDistance(distances: Seq[Double], trace: Double, ret: Int): Int = {
            if (trace <=0 || distances.isEmpty) ret
            else {
              trackBackDistance(distances.tail, trace - distances.head, ret + 1)
            }
          }

          val backDistanceEnd = trackBackDistance(gpsDist, backDistance, 0)
          val prefixValidated = prefixRaw.dropRight(backDistanceEnd)

          val timeValidated = prefixValidated.last._1

          val offsetPrefTime = timeValidated.plus(timeOffset)
          val edgeTime = if (compare(offsetPrefTime, stream.head._1)) stream.head._1 else offsetPrefTime
          (edgeTime, stream.last._1)
        }.getOrElse((stream.head._1, stream.last._1))
      }

      val droppedPrefixTime = dropEmptyPrefix(stream.toList, Seconds.seconds(-10), _ <= _)
      val droppedPostfixTime = dropEmptyPrefix(stream.toList.reverse, Seconds.seconds(+10), _ >= _)
      if (droppedPrefixTime._1 >= droppedPostfixTime._1) None
      else Some((droppedPrefixTime._1, droppedPostfixTime._1))
    } else None
  }

  lazy val distStream: DistStream = distStreamFromGPS(stream)

  private def distStreamToCSV(ds: DistStream): String = {
    ds.map(kv => s"${kv._1},${kv._2}").mkString("\n")
  }

  //noinspection ScalaUnusedSymbol
  private def rawToCSV: String = {
    val dist = distStreamFromGPS(stream)
    distStreamToCSV(dist)
  }

  //noinspection ScalaUnusedSymbol
  private def smoothedToCSV: String = {
    val dist = distStreamFromGPS(stream)
    val smooth = smoothSpeed(dist, smoothingInterval)
    distStreamToCSV(smooth)
  }

  /*
  * @param timeOffset in seconds
  * */
  private def errorToStream(offsetStream: DistStream, speedStream: DistStream): Double = {
    if (offsetStream.isEmpty || speedStream.isEmpty) {
      Double.MaxValue
    } else {
      // TODO: optimize: move speed smoothing out of this function
      def maxTime(a: ZonedDateTime, b: ZonedDateTime) = if (a>b) a else b
      def minTime(a: ZonedDateTime, b: ZonedDateTime) = if (a<b) a else b
      val begMatch = maxTime(offsetStream.head._1, startTime.get)
      val endMatch = minTime(offsetStream.last._1, endTime.get)
      // ignore non-matching parts (prefix, postfix)
      def selectInner[T](data: Seq[(ZonedDateTime, T)]) = data.dropWhile(_._1 < begMatch).takeWhile(_._1 < endMatch)
      val distToMatch = selectInner(offsetStream)

      val distPairs = distToMatch zip distToMatch.drop(1) // drop(1), not tail, because distToMatch may be empty
      val speedToMatch = distPairs.map {
        case ((aTime, aDist), (bTime, bDist)) => aTime -> aDist / Seconds.secondsBetween(aTime, bTime).getSeconds
      }
      val smoothedSpeed = selectInner(speedStream)

      def compareSpeedHistory(fineSpeed: DistStream, coarseSpeed: DistStream, error: Double): Double = {
        //
        if (fineSpeed.isEmpty || coarseSpeed.isEmpty) error
        else {
          if (fineSpeed.head._1 < coarseSpeed.head._1) compareSpeedHistory(fineSpeed.tail, coarseSpeed, error)
          else {
            def square(x: Double) = x * x
            val itemError = square(fineSpeed.head._2 - coarseSpeed.head._2)
            compareSpeedHistory(fineSpeed.tail, coarseSpeed.tail, error + itemError * itemError)
          }
        }
      }

      if (smoothedSpeed.isEmpty || speedToMatch.isEmpty) {
        Double.MaxValue
      } else {
        val error = compareSpeedHistory(smoothedSpeed, speedToMatch, 0)
        error
      }
    }

  }


  /*
  * @param 10 sec distance stream (provided by a Quest) */
  private def findOffset(distanceStream: DistStream) = {
    val maxOffset = 60
    val offsets = -maxOffset to maxOffset
    val speedStream = computeSpeedStream(distStream)
    val errors = for (offset <- offsets) yield {
      val offsetStream = distanceStream.map { case (k,v) =>
        k.plus(Seconds.seconds(offset)) -> v
      }
      errorToStream(offsetStream, speedStream)
    }
    // TODO: prefer most central best error
    val (minError, minErrorOffset) = (errors zip offsets).minBy(_._1)
    // compute confidence: how much is the one we have selected reliable?
    // the ones close may have similar metrics, that is expected, but none far away should have it


    def confidenceForSolution(offsetCandidate: Int) = {
      val confidences = (errors zip offsets).map { case (err, off) =>
        if (off == offsetCandidate) 0
        else {
          val close = 1 - (off - offsetCandidate).abs / (2 * maxOffset).toDouble
          (err - minError) * close
        }
      }

      val confidence = confidences.sum
      confidence
    }

    // smoothing causes offset in one direction
    //val empiricalOffset = 30 // this is for Quest smoothing 5 and GPS smoothing (smoothingInterval) 30
    val empiricalOffset = 18 // this is for Quest smoothing 5 and GPS smoothing (smoothingInterval) 60
    (minErrorOffset + empiricalOffset, confidenceForSolution(minErrorOffset))
  }

  def adjustHrd(hrdMove: Move): Move = {
    val hrWithDistStream = hrdMove.streamGet[DataStreamHRWithDist]
    hrWithDistStream.map { dist =>
      val distanceSums = dist.mapStreamValues(_.dist)
      val distances = (dist.stream.values.tail zip dist.stream.values).map(ab => ab._1.dist - ab._2.dist)

      def smoothDistances(todo: Iterable[Double], window: Vector[Double], done: List[Double]): List[Double] = {
        if (todo.isEmpty) done
        else {
          val smoothSize = 5
          val newWindow = if (window.size < smoothSize) window :+ todo.head
          else window.tail :+ todo.head
          smoothDistances(todo.tail, newWindow, done :+ newWindow.sum / newWindow.size)
        }
      }
      val distancesSmooth = smoothDistances(distances, Vector(), Nil)

      //val distances10x = distancesSmooth.flatMap(d => List.fill(10)(d/10)).mkString("\n")
      val distancesWithTimes = (distanceSums.keys zip distancesSmooth).toSeq
      val (bestOffset, confidence) = findOffset(distancesWithTimes)
      println(s"Quest offset $bestOffset from distance ${distanceSums.last._2}, confidence $confidence")
      //hrdMove.timeOffset(bestOffset)
      hrdMove
    }.getOrElse(hrdMove)
  }

}

case class DataStreamLap(override val stream: SortedMap[ZonedDateTime, String]) extends DataStream[String] {
  def typeToLog: String = "Laps"

  override def pickData(data: DataMap) = new DataStreamLap(data)
  override def isAlmostEmpty = false
  override def isNeeded = true
  def dropAlmostEmpty: DataStreamLap = this
}

case class DataStreamHRWithDist(override val stream: SortedMap[ZonedDateTime, HRPoint]) extends DataStream[HRPoint] {
  def typeToLog = "HRDist"

  def rebase: DataStream[HRPoint] = {
    if (stream.isEmpty) this
    else {
      val base = stream.head._2.dist
      new DataStreamHRWithDist(mapStreamValues(v => v.copy(dist = v.dist  - base)))
    }
  }

  override def isAlmostEmpty = stream.isEmpty || DataStream.distanceIsAlmostEmpty(stream.head._2.dist, stream.last._2.dist, stream.head._1, stream.last._1)
  override def isNeeded = false

  override def pickData(data: DataMap) = new DataStreamHRWithDist(data).rebase
  def dropAlmostEmpty: DataStreamHRWithDist = this // TODO: drop

}

case class DataStreamHR(override val stream: SortedMap[ZonedDateTime, Int]) extends DataStream[Int] {
  def typeToLog = "HR"

  override def pickData(data: DataMap) = new DataStreamHR(data)
  override def isAlmostEmpty = false
  override def isNeeded = false
  def dropAlmostEmpty: DataStreamHR = this // TODO: drop
}

case class DataStreamDist(override val stream: SortedMap[ZonedDateTime, Double]) extends DataStream[Double] {

  assert(stream.isEmpty || stream.head._2 <= stream.last._2)

  def typeToLog = "Dist"

  def offsetDist(dist: Double): DataStreamDist = DataStreamDist(mapStreamValues(_ + dist))

  def distanceForTime(time: ZonedDateTime): Double = {
    stream.from(time).headOption.map(_._2).getOrElse(stream.last._2)
  }



  private def rebase = {
    if (stream.isEmpty) this
    else offsetDist(- stream.head._2)
  }

  override def isAlmostEmpty = stream.isEmpty || DataStream.distanceIsAlmostEmpty(stream.head._2, stream.last._2, stream.head._1, stream.last._1)
  override def isNeeded = false

  override def pickData(data: DataMap): DataStreamDist = DataStreamDist(data).rebase
  def dropAlmostEmpty: DataStreamDist = this // TODO: drop
}

