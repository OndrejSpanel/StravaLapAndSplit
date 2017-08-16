package com.github.opengrabeso.stravamat
package moveslink2


import java.io._

import org.joda.time.{DateTimeZone, DateTime => ZonedDateTime}
import org.joda.time.format.DateTimeFormat

import Main._

import scala.collection.immutable.SortedMap
import shared.Util._

import scala.collection.mutable.ArrayBuffer

object XMLParser {
  private val PositionConstant = 57.2957795131

  private val dateFormatNoZone = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(DateTimeZone.getDefault)

  def getRRArray(rrData: String): Seq[Int] = {
    val rrArray = rrData.split(" ")
    for (rr <- rrArray) yield rr.toInt
  }

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

      def grammar = root(
        "Device" tag ("Name" text (text => deviceName = Some(text))),
        "Header" tag (
          "Distance" text (text => distance = text.toInt),
          "DateTime" text (text => startTime = Some(timeToUTC(ZonedDateTime.parse(text, dateFormatNoZone)))),
          "Duration" text (text => durationMs = (text.toDouble * 1000).toInt)
        ),
        "R-R" tag ("Data" text (text => rrData = getRRArray(text))),
        "Samples" tag (
          "Sample" tagWithOpen (
            samples += new Sample,
            "Latitude" text (text => samples.last.latitude = Some(text.toDouble * XMLParser.PositionConstant)),
            "Longitude" text (text => samples.last.longitude = Some(text.toDouble * XMLParser.PositionConstant)),
            "GPSAltitude" text (text => samples.last.elevation = Some(text.toInt)),
            // TODO: handle relative time when UTC is not present
            "UTC" text (text => samples.last.time = Some(ZonedDateTime.parse(text))),
            "Distance" text (text => samples.last.distance = Some(text.toDouble)),
            "HR" text (text => samples.last.heartRate = Some(text.toInt)),
            // TODO: add other properties (power, cadence, temperature ...)

            "Events" tag (
              "Pause" tag ("State" text (text => paused = text.equalsIgnoreCase("true"))),
              "Lap" tag (
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
          )
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