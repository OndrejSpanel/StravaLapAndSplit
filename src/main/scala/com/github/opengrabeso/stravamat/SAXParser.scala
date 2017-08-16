package com.github.opengrabeso.stravamat

import java.io.InputStream

import com.fasterxml.aalto.sax.SAXParserFactoryImpl
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
//import org.xml.sax.Attributes
//import org.xml.sax.helpers.DefaultHandler

import scala.collection.mutable.ArrayBuffer

object SAXParser {

  trait Events {
    def open(path: Seq[String])
    def read(path: Seq[String], text: String)
    def close(path: Seq[String])
  }

  // reverse :: associativity so that paths can be written in a natural order
  object / {
    def unapply(arg: Seq[String]): Option[(Seq[String], String)] = {
      arg match {
        case head +: tail => Some(tail, head)
        case _ => None
      }
    }
  }

  def parse(doc: InputStream)(handler: Events) = {

    var path = List.empty[String]

    object SaxHandler extends DefaultHandler {

      val elementStack = ArrayBuffer.empty[StringBuilder]
      override def startElement(uri: String, localName: String, qName: String, attributes: Attributes) = {
        path = localName :: path
        elementStack += new StringBuilder()
        handler.open(path)
      }

      override def endElement(uri: String, localName: String, qName: String) = {
        if (path.headOption.contains(localName)) {
          val text = elementStack.remove(elementStack.size - 1)
          handler.read(path, text.mkString)
          handler.close(path)
          path = path.tail
        } else {
          throw new UnsupportedOperationException(s"Unexpected tag end `$localName`")
        }
      }

      override def characters(ch: Array[Char], start: Int, length: Int) = {
        elementStack.last ++= ch.slice(start, start + length)
      }
    }

    val factory = SAXParserFactoryImpl.newInstance()
    val p = factory.newSAXParser()
    p.parse(doc, SaxHandler)
  }


  trait TagHandler {
    def open() = ()
    def text(s: String) = ()
    def close() = ()
  }
  class XMLTag(val name: String, val inner: XMLTag*) extends TagHandler

  class ProcessText(name: String, process: String => Unit) extends XMLTag(name) {
    override def text(s: String) = process(s)
  }


  trait SAXParserWithGrammar extends Events {
    def grammar: XMLTag

    var inTags = List(grammar)
    // when closing, we need to know for which tags open was called
    var known = List.empty[Boolean]

    def open(path: Seq[String]) = {

      val descendInto = inTags.head.inner.find(_.name == path.head) // TODO: optimize using Map
      for (into <- descendInto) {
        inTags = into :: inTags
        into.open()
      }
      known = descendInto.isDefined :: known
    }

    def read(path: Seq[String], text: String) = {
      if (known.head) {
        inTags.head.text(text)
      }
    }

    def close(path: Seq[String]) = {
      if (known.head) {
        assert(inTags.head.name == path.head)
        inTags.head.close()
        inTags = inTags.tail
      }
      known = known.tail
    }


  }
}
