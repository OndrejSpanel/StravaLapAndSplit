package com.github.opengrabeso.stravamat

import scala.io.Source
import scala.xml.{MetaData, NamespaceBinding, NodeSeq}
import scala.xml.parsing.{ExternalSources, MarkupHandler, MarkupParser}

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

  def parse(doc: Source)(handler: Events) = {

    var path = List.empty[String]

    class XMLSAXParser(override val input: Source) extends MarkupHandler with MarkupParser with ExternalSources {
      override def elem(pos: Int, pre: String, label: String, attrs: MetaData, scope: NamespaceBinding, empty: Boolean, args: NodeSeq) = {
        NodeSeq.Empty
      }

      override def elemStart(pos: Int, pre: String, label: String, attrs: MetaData, scope: NamespaceBinding) {
        path = label :: path
        handler.open(path)
      }
      override def elemEnd(pos: Int, pre: String, label: String): Unit = {
        if (path.headOption.contains(label)) {
          handler.close(path)
          path = path.tail
        } else {
          throw new UnsupportedOperationException(s"Unexpected tag end `$label`")
        }

      }

      override def text(pos: Int, text: String) = {
        handler.read(path, text)
        NodeSeq.Empty
      }

      override def procInstr(pos: Int, target: String, txt: String) = NodeSeq.Empty

      override def comment(pos: Int, comment: String) = NodeSeq.Empty

      override def entityRef(pos: Int, n: String) = NodeSeq.Empty

      override val preserveWS = true

      def parse() = {
        nextch()
        document()
      }
    }

    val p = new XMLSAXParser(doc)
    p.parse()
  }
}
