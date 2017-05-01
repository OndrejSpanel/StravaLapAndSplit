package com.github.opengrabeso.stravalas

@SerialVersionUID(10L)
trait FileId {
  def filename: String
}

object FileId {

  def parse(actId: String): FileId = {
    if (actId.last == ')') {
      val prefix = actId.takeWhile(_ != '(')
      val content = actId.drop(prefix.length + 1).dropRight(1)

      prefix match {
        case "FilenameId" =>
          FilenameId(content)
        case "StravaId" =>
          StravaId(content.toLong)
      }
    } else {
      throw new UnsupportedOperationException("Bad activity id")
    }
  }

  case class StravaId(id: Long) extends FileId {
    def filename = "events-" + id.toString
  }
  case class FilenameId(id: String) extends FileId {
    def filename = id
  }
  case object NoId extends FileId {
    def filename = ""
  }
}
