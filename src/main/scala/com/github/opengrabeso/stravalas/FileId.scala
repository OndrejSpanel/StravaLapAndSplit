package com.github.opengrabeso.stravalas

@SerialVersionUID(10L)
trait FileId {
  def filename: String
  def stravaId: String
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

  @SerialVersionUID(10L)
  case class StravaId(id: Long) extends FileId {
    def filename = "events-" + id.toString
    def stravaId = id.toString
  }
  @SerialVersionUID(10L)
  case class FilenameId(id: String) extends FileId {
    def filename = id
    def stravaId = ""
  }
  @SerialVersionUID(10L)
  case object NoId extends FileId {
    def filename = ""
    def stravaId = ""
  }
}
