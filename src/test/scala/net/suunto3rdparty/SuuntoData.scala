package net.suunto3rdparty

import scala.xml.XML

trait SuuntoData {
  protected def gpsPodMove = {
    val res = getClass.getResourceAsStream("/suuntoMerge/Moveslink2/gps.sml")

    val doc = moveslink2.XMLParser.getDeviceLog(XML.load(res))
    val move = moveslink2.XMLParser.parseXML("gps.sml", doc)
    move
  }

  protected def questMove = {
    val res = getClass.getResourceAsStream("/suuntoMerge/Moveslink/quest.xml")
    val doc = XML.load(res)

    val move = moveslink.XMLParser.parseXML("quest.xml", doc)
    move.flatMap(_.toOption)
  }


}
