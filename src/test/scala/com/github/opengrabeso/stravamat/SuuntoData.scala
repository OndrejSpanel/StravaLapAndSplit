package com.github.opengrabeso.stravamat

import org.joda.time.DateTimeZone

import scala.util.Try

trait SuuntoData {
  protected def gpsPodMove: Try[Move] = {
    val res = getClass.getResourceAsStream("/suuntoMerge/Moveslink2/gps.sml")

    val move = moveslink2.XMLParser.parseXML("gps.sml", res)
    move
  }

  protected def questMove: Seq[Move] = {
    val res = getClass.getResourceAsStream("/suuntoMerge/Moveslink/quest.xml")

    val doc = moveslink.XMLParser.skipMoveslinkDoctype(res)
    val localTimeZone = DateTimeZone.getDefault.toString

    val move = moveslink.XMLParser.parseXML("quest.xml", doc, 240, localTimeZone)
    move
  }


}
