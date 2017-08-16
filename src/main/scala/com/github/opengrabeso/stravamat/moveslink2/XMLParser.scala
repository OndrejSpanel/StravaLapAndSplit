package com.github.opengrabeso.stravamat
package moveslink2


import java.io._

import org.joda.time.{DateTimeZone, DateTime => ZonedDateTime}
import org.joda.time.format.DateTimeFormat
import org.apache.commons.math.ArgumentOutsideDomainException
import org.apache.commons.math.analysis.interpolation.SplineInterpolator
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction
import java.util.logging.Logger

import Main._
import com.github.opengrabeso.stravamat.SAXParser.ProcessText

import scala.collection.immutable.SortedMap
import shared.Util._

import scala.collection.mutable.ArrayBuffer

object XMLParser {
  private val log = Logger.getLogger(XMLParser.getClass.getName)
  private val PositionConstant = 57.2957795131

  private val dateFormatNoZone = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(DateTimeZone.getDefault)

  def interpolate(spline: PolynomialSplineFunction, x: Double): Double = {
    try {
      spline.value(x)
    }
    catch {
      case _: ArgumentOutsideDomainException =>
        val knots = spline.getKnots
        spline.value(knots(if (x < knots(0)) 0 else spline.getN - 1))
    }
  }
  def generateTimeToDistanceSplineFunction(timeArray: Seq[Double], distanceArray: Seq[Double]): PolynomialSplineFunction = {
    val interpolator = new SplineInterpolator
    interpolator.interpolate(timeArray.toArray, distanceArray.toArray)
  }
  def populateDistanceArray(distanceList: Seq[Double]): Seq[Double] = {
    for (d <- distanceList) yield d
  }

  def populateHRArray(hrList: Seq[Double]): Seq[Double] = {
    for (hr <- hrList) yield hr * 60
  }
  def generateTimeToHRSplineFunction(timeArray: Seq[Double], hrArray: Seq[Double]): PolynomialSplineFunction = {
    val interpolator = new SplineInterpolator
    interpolator.interpolate(timeArray.toArray, hrArray.toArray)
  }

  def getRRArray(rrData: String): Seq[Int] = {
    val rrArray = rrData.split(" ")
    for (rr <- rrArray) yield rr.toInt
  }

  /*
  def parseSamples(fileName: String, header: Header, samples: NodeSeq, rr: Seq[Int]): Move = {
    val sampleList = samples \ "Sample"
    val lapPoints = {
      /* GPS Track Pod lap is stored as:
			<Sample>
				<UTC>2016-03-18T10:19:31</UTC>
				<Time>24.891</Time>
      	<Events>
					<Lap>
						<Type>Manual</Type>
						<Duration>24.9</Duration>
						<Distance>15</Distance>
					</Lap>
				</Events>
			</Sample>
      */
      sampleList.toList.flatMap { sample =>
        val lapTime = Try {
          //noinspection ScalaUnusedSymbol
          for (lap <- (sample \ "Events" \ "Lap" \ "Type").headOption) yield {
            val timestamp = (sample \ "UTC") (0).text
            val utc = timeToUTC(ZonedDateTime.parse(timestamp, dateFormatNoZone))
            Lap(lap.text, utc)
          }
        }

        lapTime.toOption.flatten.toSeq
      }
    }

    val periodicSamples = {
      val paused = new PauseState
      sampleList.flatMap { sample =>
        paused.trackPause(sample)
        if (!paused.inPause) {
          val periodicSample = for {
            sampleType <- Try((sample \ "SampleType")(0).text) if sampleType.equalsIgnoreCase("periodic")
            distanceStr <- Try((sample \ "Distance")(0).text)
            timeStr <- Try((sample \ "Time")(0).text)
            time = timeStr.toDouble - paused.pausedTime
          } yield {
            val hrTry = Try((sample \ "HR")(0).text)
            val elevationTry = Try((sample \ "Altitude")(0).text)
            val timeTry = Try(ZonedDateTime.parse((sample \ "UTC")(0).text))
            val timeSim = header.startTime.plusMillis((time * 1000).toInt)
            // prefer UTC when present
            val timeUtc = timeTry.getOrElse(timeSim)
            // replace missing values with zeroes - this is what Quest is recording on failure anyway
            val hr = hrTry.map(_.toInt).getOrElse(0)
            val elevation = elevationTry.map(_.toInt).toOption
            (timeUtc, distanceStr.toDouble, hr, elevation)
          }
          periodicSample.toOption
        } else None
      }
    }

    object Unzipped4 {
      def unapply[A, B, C, D](ts: Seq[(A, B, C, D)]): Some[(Seq[A], Seq[B], Seq[C], Seq[D])] =
        Some((ts.map(_._1), ts.map(_._2), ts.map(_._3), ts.map(_._4)))

    }

    // ignore elevation: let Strava compute it
    val Unzipped4(timeSeq, distanceSeq, hrSeq, _) = periodicSamples

    val hrStream = if (hrSeq.size == distanceSeq.size && hrSeq.exists(_ != 0)) {
      Some(new DataStreamHR(SortedMap(timeSeq zip hrSeq:_*)))
    } else None

    val distStream = new DataStreamDist(SortedMap(timeSeq zip distanceSeq:_*))

    val gpsStream = new DataStreamGPS(SortedMap(trackPoints:_*))

    val lapStream = if (lapPoints.nonEmpty) {
      Some(new DataStreamLap(SortedMap(lapPoints.map(l => l.timestamp -> l.name): _*)))
    } else None

    val gpsMove = new Move(Set(fileName), header.moveHeader, Seq(gpsStream, distStream) ++ lapStream ++ hrStream:_*)

    val gpsDroppedEmpty = gpsStream.dropAlmostEmpty match {
      case Some((keepStart, keepEnd)) =>
        gpsMove.span(keepStart)._2.flatMap(_.span(keepEnd)._1)
      case None =>
        None
    }
    gpsDroppedEmpty.getOrElse(new Move(Set(fileName), header.moveHeader))
  }
  */

  def parseXML(fileName: String, inputStream: InputStream, digest: String): Option[ActivityEvents] = {

    import SAXParser._

    object parsed extends SAXParserWithGrammar {
      var rrData = Seq.empty[Int]
      var deviceName = Option.empty[String]
      var startTime = Option.empty[ZonedDateTime]
      var distance: Int = 0
      var durationMs: Int = 0
      var paused: Boolean = false
      var pauseStartTime = Option.empty[ZonedDateTime]
      class Sample{
        /* GPS Track Pod example:
        <Sample>
          <Latitude>0.86923005364868888</Latitude>
          <Longitude>0.24759951117797119</Longitude>
          <GPSAltitude>416</GPSAltitude>
          <GPSHeading>1.4116222990130136</GPSHeading>
          <EHPE>4</EHPE>
          <Time>2534</Time>
          <UTC>2016-10-21T07:28:14Z</UTC>
        </Sample>
        <Sample>
          <VerticalSpeed>0</VerticalSpeed>
          <Distance>7868</Distance>
          <Speed>3.9399999999999999</Speed>
          <Time>2534.6120000000001</Time>
          <SampleType>periodic</SampleType>
          <UTC>2016-10-21T07:28:14.612Z</UTC>
        </Sample>
        */
        var time = Option.empty[ZonedDateTime]
        var distance = Option.empty[Double]
        var latitude = Option.empty[Double]
        var longitude = Option.empty[Double]
        var elevation: Option[Int] = None
        var heartRate: Option[Int] = None
      }
      val samples = ArrayBuffer.empty[Sample]
      val laps = ArrayBuffer.empty[Lap]

      def grammar = new XMLTag("<root>",
        new XMLTag("Device", "Name" text (text => deviceName = Some(text))),
        new XMLTag("Header",
          "Distance" text (text => distance = text.toInt),
          "DateTime" text (text => startTime = Some(timeToUTC(ZonedDateTime.parse(text, dateFormatNoZone)))),
          "Duration" text (text => durationMs = (text.toDouble * 1000).toInt)
        ),
        new XMLTag("R-R", "Data" text (text => rrData = getRRArray(text))),
        new XMLTag("Samples",
          new XMLTag("Sample",
            "Latitude" text (text => samples.last.latitude = Some(text.toDouble * XMLParser.PositionConstant)),
            "Longitude" text (text => samples.last.longitude = Some(text.toDouble * XMLParser.PositionConstant)),
            "GPSAltitude" text (text => samples.last.elevation = Some(text.toInt)),
            // TODO: handle relative time when UTC is not present
            "UTC" text (text => samples.last.time = Some(ZonedDateTime.parse(text))),
            "Distance" text (text => samples.last.distance = Some(text.toDouble)),
            "HR" text (text => samples.last.heartRate = Some(text.toInt)),
            // TODO: add other properties (power, cadence, temperature ...)

            new XMLTag("Events",
              new XMLTag("Pause", "State" text (text => paused = text.equalsIgnoreCase("true"))),
              new XMLTag("Lap",
                "Type" text { text =>
                  val lastTime = samples.reverseIterator.flatMap(_.time) //.find(_.isDefined)
                  for (timestamp <- lastTime.toIterable.headOption) {
                    laps += Lap(text, timestamp)
                  }
                }
                // we are not interested about any Lap properties
                //ProcessText("Duration", ???),
                //ProcessText("Distance", ???)
              )
            )

          ) {override def open() = samples += new Sample}
        )
      )
    }

    SAXParser.parse(inputStream)(parsed)

    // always check time last, as this is present in almost each entry. We want first check to filter out as much as possible
    val gpsSamples = for {
      s <- parsed.samples
      longitude <- s.longitude
      latitude <- s.latitude
      time <- s.time
    } yield {
      time -> GPSPoint(latitude, longitude, s.elevation)
    }

    val ret = for (gpsInterestingRange <- DataStreamGPS.dropAlmostEmpty(gpsSamples.toList)) yield {

      def inRange(t: ZonedDateTime) = t >= gpsInterestingRange._1 && t <= gpsInterestingRange._2

      val distSamples = for {
        s <- parsed.samples
        distance <- s.distance
        time <- s.time if inRange(time)
      } yield {
        time -> distance
      }
      val hrSamples = for {
        s <- parsed.samples
        v <- s.heartRate
        time <- s.time if inRange(time)
      } yield {
        time -> v
      }


      val gpsStream = new DataStreamGPS(SortedMap(gpsSamples.filter(s => inRange(s._1)): _*))
      val distStream = new DataStreamDist(SortedMap(distSamples: _*))

      val hrStream = if (hrSamples.exists(_._2 != 0)) Some(new DataStreamHR(SortedMap(hrSamples: _*))) else None

      val lapTimes = parsed.laps.map(_.timestamp).filter(inRange)

      // TODO: read ActivityType from XML
      val sport = Event.Sport.Workout

      val allStreams = Seq(distStream, gpsStream) ++ hrStream

      val activity = for {
        startTime <- allStreams.flatMap(_.startTime).minOpt
        endTime <- allStreams.flatMap(_.endTime).maxOpt
        d <- distStream.stream.lastOption.map(_._2)
      } yield {

        val id = ActivityId(FileId.FilenameId(fileName), digest, "Activity", startTime, endTime, sport, d)

        val events = Array[Event](BegEvent(id.startTime, sport), EndEvent(id.endTime))

        // TODO: avoid duplicate timestamp events
        val lapEvents = lapTimes.map(LapEvent)

        val allEvents = (events ++ lapEvents).sortBy(_.stamp)

        ActivityEvents(id, allEvents, distStream, gpsStream, hrStream.toSeq)
      }
      activity
    }


    ret.flatten

  }

}