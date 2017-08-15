package com.github.opengrabeso.stravamat
package moveslink2


import java.io._

import org.joda.time.{DateTimeZone, DateTime => ZonedDateTime}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.apache.commons.math.ArgumentOutsideDomainException
import org.apache.commons.math.analysis.interpolation.SplineInterpolator
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction
import java.util.logging.Logger

import scala.collection.immutable.SortedMap
import scala.util._
import shared.Util._

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.xml.pull.XMLEventReader

object XMLParser {
  private val log = Logger.getLogger(XMLParser.getClass.getName)
  private val PositionConstant = 57.2957795131

  private val dateFormat = ISODateTimeFormat.dateTimeNoMillis
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

  def parse(fileName: String, xmlFile: File): Try[Move] = {
    XMLParser.log.fine("Parsing " + xmlFile.getName)

    val file = Source.fromFile(xmlFile)
    val doc = new XMLEventReader(file)

    parseXML(fileName, doc)
  }

  def parseXML(fileName: String, doc: XMLEventReader): Try[Move] = {

    import SAXParser._
    object parsed extends Events {
      var rrData = Seq.empty[Int]
      var deviceName = Option.empty[String]
      var startTime = Option.empty[ZonedDateTime]
      var distance: Int = 0
      var durationMs: Int = 0
      var paused: Boolean = false
      var pauseStartTime = Option.empty[ZonedDateTime]
      class Sample{
        var time = Option.empty[ZonedDateTime]
        var distance = Option.empty[Double]
        var latitude = Option.empty[Double]
        var longitude = Option.empty[Double]
        var elevation: Option[Int] = None
        var hr: Option[Int] = None
      }
      val samples = ArrayBuffer.empty[Sample]

      def open(path: Seq[String]) = {
        path match {
          case _ / "Samples" / "Sample" =>
            samples append new Sample
          case _ =>
        }
      }

      def read(path: Seq[String], text: String) = {
        path match {
          case  _ / "Header" / "Device" / "Name" =>
            deviceName = Some(text)
          case  _ / "Header" / "Distance" =>
            distance = text.toInt
          case  _ / "Header" / "DateTime" =>
            startTime = Some(timeToUTC(ZonedDateTime.parse(text, dateFormatNoZone)))
          case  _ / "Header" / "Duration" =>
            durationMs = (text.toDouble * 1000).toInt
          case _ / "R-R" / "Data" =>
            rrData = getRRArray(text)
          case _ / "Events" / "Pause" / "State" =>
            paused = text.equalsIgnoreCase("true")

          // TODO: profile performance of testing "Sample" again and again while inside of the sample
          case _ / "Sample" / "Latitude" =>
            samples.last.latitude = Some(text.toDouble * XMLParser.PositionConstant)
          case _ / "Sample" / "Longitude" =>
            samples.last.longitude = Some(text.toDouble * XMLParser.PositionConstant)
          case _ / "Sample" / "GPSAltitude" =>
            samples.last.elevation = Some(text.toInt)
          case _ / "Sample" / "UTC" =>
            samples.last.time = Some(ZonedDateTime.parse(text))

          case _ =>
        }
      }

      def close(path: Seq[String]) = {}
    }
    SAXParser.parse(doc)(parsed)

    ???
  }

}