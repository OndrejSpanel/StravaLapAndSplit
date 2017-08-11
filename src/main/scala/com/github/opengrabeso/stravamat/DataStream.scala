package com.github.opengrabeso.stravamat

import org.joda.time.{ReadablePeriod, Seconds, DateTime => ZonedDateTime}

import scala.collection.immutable.SortedMap
import shared.Util._
import shared.Timing

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
@SerialVersionUID(10L)
sealed abstract class DataStream extends Serializable {

  def typeToLog: String
  def streamType: Class[_ <: DataStream] = this.getClass

  type Item

  type DataMap = SortedMap[ZonedDateTime, Item]

  def stream: DataMap

  def pickData(data: DataMap): DataStream

  def mapStreamValues[T](f: Item => T): SortedMap[ZonedDateTime, T] = DataStream.mapStreamValues(stream, f)

  val startTime: Option[ZonedDateTime] = stream.headOption.map(_._1)
  val endTime: Option[ZonedDateTime] = stream.lastOption.map(_._1)

  def inTimeRange(b: ZonedDateTime, e: ZonedDateTime): Boolean = {
    startTime.forall(_ >= b) && endTime.forall(_ <= e)
  }

  // should be discarded
  def isAlmostEmpty: Boolean

  // must not be discarded
  def isNeeded: Boolean

  def span(time: ZonedDateTime): (DataStream, DataStream) = {
    val (take, left) = stream.span(_._1 < time)
    (pickData(take), pickData(left))
  }

  def slice(timeBeg: ZonedDateTime, timeEnd: ZonedDateTime): DataStream = {

    val subData = stream.filter(i => i._1 >= timeBeg && i._1 <= timeEnd)

    pickData(subData)
  }

  def timeOffset(bestOffset: Int): DataStream = {
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


  private type DistStream  = SortedMap[ZonedDateTime, Double]
  private type DistList  = List[(ZonedDateTime, Double)]

  // median, 80% percentile, max
  case class SpeedStats(median: Double, fast: Double, max: Double)
  /**
    * Experiments have shown smoothingInterval = 60 gives most accurate results.
    * Perhaps the same smoothing interval is used in the Quest itself?
    */
  private val smoothingInterval = 60

  /**
    * Quest records sometimes miss one sample, the missing sample is added the a neighboring sample, like:
    * 2016-04-13T09:47:01Z	4.9468178241
    * 2016-04-13T09:47:02Z	10.5000627924
    * 2016-04-13T09:47:04Z	5.2888359044
    */
  private def fixSpeed(input: DistStream): DistStream = {
    def fixSpeedRecurse(input: DistStream, done: DistStream): DistStream = {
      if (input.isEmpty) done
      else {
        if (input.tail.isEmpty) fixSpeedRecurse(input.tail, done + input.head)
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
              val addMissing = for (s <- 1 to missingCount.toInt) yield fixed0.copy(_1 = item0._1.plusSeconds(s))
              fixSpeedRecurse(input.tail, done + fixed0 ++ addMissing)
            } else {
              // 0 small, 1 missing, 2 large
              val fixed2 = item1.copy(_2 = value0 / duration)
              val addMissing = for (s <- 1 to missingCount.toInt) yield fixed2.copy(_1 = item0._1.plusSeconds(s))
              fixSpeedRecurse(input.tail.tail, done + item0 ++ addMissing + fixed2)
            }
          } else {
            fixSpeedRecurse(input.tail, done + input.head)
          }

        }
      }
    }

    fixSpeedRecurse(input, SortedMap())
  }

  private def smoothSpeed(input: DistStream, durationSec: Double): DistStream = {
    // TODO: optimize, this is currently very slow (processing 2 hours or run data takes 30 seconds)
    type Window = Vector[(ZonedDateTime, Double)]
    def smoothingRecurse(done: DistList, prev: Window, todo: DistList): DistList = {
      if (todo.isEmpty) done
      else if (prev.isEmpty) {
        smoothingRecurse(todo.head +: done, prev :+ todo.head, todo.tail)
      } else {
        def durationWindow(win: Window) = Seconds.secondsBetween(win.head._1, win.last._1).getSeconds
        def keepWindow(win: Window): Window = if (durationWindow(win) <= durationSec) win else keepWindow(win.tail)
        val newWindow = keepWindow(prev :+ todo.head)
        val duration = durationWindow(newWindow)
        val windowSpeed = if (duration > 0) prev.map(_._2).sum / duration else 0.0
        val interval = Seconds.secondsBetween(prev.last._1, todo.head._1).getSeconds
        val smoothDist = (windowSpeed * duration + todo.head._2) / ( duration + interval)
        smoothingRecurse((todo.head._1 -> smoothDist) +: done, newWindow, todo.tail)
      }
    }

    // fixSpeed(input) was called here, but it was used only because of sample timestamp misunderstanding
    val smoothedList = smoothingRecurse(Nil, Vector(), input.toList)
    SortedMap(smoothedList.reverse:_*)
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
    SortedMap((gpsKeys.head -> 0.0) +: gpsDistances:_*)
  }

  def routeStreamFromDistStream(distDeltas: DistStream): DistStream = {
    val route = distDeltas.scanLeft(0d) { case (sum, (_, d)) => sum + d }
    // scanLeft adds initial value as a first element - use tail to drop it
    val ret = distDeltas.map(_._1) zip route.tail
    SortedMap(ret.toSeq:_*)
  }

  def distStreamFromRouteStream(dist: DistStream): DistStream = {
    val times = dist.map(_._1).toSeq
    val routeValues = dist.map(_._2).toSeq
    val distValues = 0.0 +: (routeValues zip routeValues.drop(1)).map(p => p._2 - p._1)
    val ret = times zip distValues
    SortedMap(ret:_*)
  }

  def routeStreamFromSpeedStream(distDeltas: DistStream): DistStream = {
    if (distDeltas.isEmpty) SortedMap()
    else {
      assert(distDeltas.head._2 == 0)
      val route = distDeltas.tail.scanLeft(distDeltas.head) { case ((tSum, dSum), (t, d)) =>
        val dt = Seconds.secondsBetween(tSum, t).getSeconds
        t -> (dSum + d * dt)
      }
      route
    }
  }

  def computeSpeedStream(dist: DistStream, smoothing: Int = 10): DistStream = {
    implicit val start = Timing.Start()
    val smoothedSpeed = smoothSpeed(dist, smoothing)
    Timing.logTime(s"computeSpeedStream of ${dist.size} samples, smoothing $smoothing")
    smoothedSpeed
  }

  /**
    * @return median, 80% percentile, max
    * */
  def speedStats(speedStream: DistStream): SpeedStats = {
    implicit val start = Timing.Start()

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


    val med = median(speeds.toSeq)

    val fast = percentile(80)

    Timing.logTime(s"Speed of ${speedStream.size} samples")
    SpeedStats(med, fast, max)
  }


}

@SerialVersionUID(10L)
class DataStreamGPS(override val stream: SortedMap[ZonedDateTime, GPSPoint]) extends DataStream {

  import DataStreamGPS._

  type Item = GPSPoint

  def typeToLog: String = "GPS"

  override def pickData(data: DataMap) = new DataStreamGPS(data)
  override def slice(timeBeg: ZonedDateTime, timeEnd: ZonedDateTime): DataStreamGPS = super.slice(timeBeg, timeEnd).asInstanceOf[DataStreamGPS]
  override def timeOffset(bestOffset: Int): DataStreamGPS = super.timeOffset(bestOffset).asInstanceOf[DataStreamGPS]


  override def span(time: ZonedDateTime): (DataStreamGPS, DataStreamGPS) = {
    val ret = super.span(time)
    (ret._1.asInstanceOf[DataStreamGPS], ret._2.asInstanceOf[DataStreamGPS])
  }

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


  private def distStreamToCSV(ds: DistStream): String = {
    val times = ds.map(_._1)
    val diffs = 0L +: (times zip times.drop(1)).map { case (t1, t2) => t2.getMillis - t1.getMillis }.toSeq
    (ds zip diffs).map { case (kv, duration) =>
      s"${kv._1},${duration/1000.0},${kv._2}"
    }.mkString("\n")
  }

  lazy val distStream: DistStream = distStreamFromGPS(stream)


  private def computeSpeedStream: DistStream = {

    val gpsDistances = distStreamFromGPS(stream)

    val smoothedSpeed = smoothSpeed(gpsDistances, smoothingInterval)
    smoothedSpeed
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
  private def errorToStream(offsetStream: DistList, speedStream: DistList): Double = {
    if (offsetStream.isEmpty || speedStream.isEmpty) {
      Double.MaxValue
    } else {
      // TODO: optimize: move speed smoothing out of this function
      def maxTime(a: ZonedDateTime, b: ZonedDateTime) = if (a>b) a else b
      def minTime(a: ZonedDateTime, b: ZonedDateTime) = if (a<b) a else b
      val begMatch = maxTime(offsetStream.head._1, startTime.get)
      val endMatch = minTime(offsetStream.last._1, endTime.get)
      // ignore non-matching parts (prefix, postfix)
      def selectInner[T](data: List[(ZonedDateTime, T)]) = data.dropWhile(_._1 < begMatch).takeWhile(_._1 < endMatch)
      val distToMatch = selectInner(offsetStream)

      val distPairs = distToMatch zip distToMatch.drop(1) // drop(1), not tail, because distToMatch may be empty
      val speedToMatch = distPairs.map {
        case ((aTime, aDist), (bTime, bDist)) => aTime -> aDist / Seconds.secondsBetween(aTime, bTime).getSeconds
      }
      val smoothedSpeed = selectInner(speedStream)

      def compareSpeedHistory(fineSpeed: DistList, coarseSpeed: DistList, error: Double): Double = {
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
        val error = compareSpeedHistory(smoothedSpeed.toList, speedToMatch.toList, 0)
        error
      }
    }

  }

  /*
  * @param 10 sec distance stream (provided by a Quest) */
  private def findOffset(distanceStream: DistStream) = {
    val distanceList = distanceStream.toList
    val maxOffset = 60
    val offsets = -maxOffset to maxOffset
    val speedStream = computeSpeedStream.toList
    val errors = for (offset <- offsets) yield {
      val offsetStream = distanceList.map { case (k,v) =>
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
    val empiricalOffset = 13 // this is for Quest smoothing 5 and GPS smoothing (smoothingInterval) 60
    (minErrorOffset + empiricalOffset, confidenceForSolution(minErrorOffset))
  }

  def adjustHrdStream(dist: DataStreamDist#DataMap): Int = {

    // try first: assume user stops watch first, GPS pod quickly after, i.e. offset can be determined based on the end times

    val gpsAfterHrd = 3000 // time in ms it takes to stop GPS after stopping HR

    // match values: stream.last._1 = dist.stream.last._1 + xxxx + gpsAfterHrd

    val endOffset = (stream.last._1.getMillis - dist.last._1.getMillis - gpsAfterHrd).toInt

    if (false) {
      val distances = (dist.values.tail zip dist.values).map(ab => ab._1 - ab._2)

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
      val distancesWithTimes = SortedMap((dist.keys zip distancesSmooth).toSeq: _*)
      val (bestOffset, confidence) = findOffset(distancesWithTimes)
      println(s"Quest offset $bestOffset from distance ${dist.last._2}, confidence $confidence")
    }
    println(s"Offset based on stop time: $endOffset")
    val useEndOffset = false
    //hrdMove.timeOffset(bestOffset)
    if (useEndOffset && endOffset.abs < 10) {
      // some smart verification between estimated and measured end offset
      (endOffset / 1000.0).round.toInt
    } else {
      0
    }
  }

  def adjustHrd(hrdMove: Move): Move = {

    val hrWithDistStream = hrdMove.streamGet[DataStreamHRWithDist]
    hrWithDistStream.map { dist =>
      val offset = adjustHrdStream(dist.mapStreamValues(_.dist))
      hrdMove.timeOffset(offset)
    }.getOrElse(hrdMove)
  }

}

@SerialVersionUID(10L)
class DataStreamLap(override val stream: SortedMap[ZonedDateTime, String]) extends DataStream {
  type Item = String

  def typeToLog: String = "Laps"

  override def pickData(data: DataMap) = new DataStreamLap(data)
  override def isAlmostEmpty = false
  override def isNeeded = true
  def dropAlmostEmpty: DataStreamLap = this
}

@SerialVersionUID(10L)
class DataStreamHRWithDist(override val stream: SortedMap[ZonedDateTime, HRPoint]) extends DataStream {
  type Item = HRPoint

  def typeToLog = "HRDist"

  def rebase: DataStream = {
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

@SerialVersionUID(10L)
class DataStreamHR(override val stream: SortedMap[ZonedDateTime, Int]) extends DataStream {
  type Item = Int

  def typeToLog = "HR"

  override def pickData(data: DataMap) = new DataStreamHR(data)
  override def isAlmostEmpty = false
  override def isNeeded = false
  def dropAlmostEmpty: DataStreamHR = this // TODO: drop
}

@SerialVersionUID(10L)
class DataStreamDist(override val stream: SortedMap[ZonedDateTime, Double]) extends DataStream {

  def typeToLog = "Dist"

  type Item = Double

  def rebase: DataStreamDist = {
    if (stream.isEmpty) this
    else {
      val base = stream.head._2
      new DataStreamDist(mapStreamValues(_ - base))
    }
  }

  def offsetDist(dist: Double): DataStreamDist = new DataStreamDist(mapStreamValues(_ + dist))

  def distanceForTime(time: ZonedDateTime): Double = {
    stream.from(time).headOption.map(_._2).getOrElse(stream.last._2)
  }

  override def isAlmostEmpty = stream.isEmpty || DataStream.distanceIsAlmostEmpty(stream.head._2, stream.last._2, stream.head._1, stream.last._1)
  override def isNeeded = false

  override def pickData(data: DataMap): DataStreamDist = new DataStreamDist(data).rebase
  override def slice(timeBeg: ZonedDateTime, timeEnd: ZonedDateTime): DataStreamDist = super.slice(timeBeg, timeEnd).asInstanceOf[DataStreamDist]
  override def timeOffset(bestOffset: Int): DataStreamDist = super.timeOffset(bestOffset).asInstanceOf[DataStreamDist]

  override def span(time: ZonedDateTime): (DataStreamDist, DataStreamDist) = {
    val ret = super.span(time)
    (ret._1.asInstanceOf[DataStreamDist], ret._2.asInstanceOf[DataStreamDist])
  }


  def dropAlmostEmpty: DataStreamDist = this // TODO: drop
}

