package com.github.opengrabeso.stravamat

import scala.xml.pull._

object SAXParser {

  trait Events {
    def open(path: Seq[String])
    def read(path: Seq[String], text: String)
    def close(path: Seq[String])
  }
  def parse(doc: XMLEventReader)(handler: Events) = {

    var path = List.empty[String]
    while (doc.hasNext) {
      val ev = doc.next()
      ev match {
        case EvElemStart(_, tag, _, _) =>
          path = tag :: path
          handler.open(path)
        case EvText(text) =>
          handler.read(path, text)
        case EvElemEnd(_, tag) if path.headOption.contains(tag) =>
          handler.close(path)
          path = path.tail
        case EvElemEnd(_, tag) =>
          throw new UnsupportedOperationException(s"Unexpected tag end `$tag`")
        case x =>
          println(s"Unexpected $x")
      }
    }
  }
}
