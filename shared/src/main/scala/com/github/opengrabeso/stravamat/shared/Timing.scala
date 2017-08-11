package com.github.opengrabeso.stravamat.shared

object Timing {
  def now() = System.currentTimeMillis()
  def logTime(msg: String)(implicit from: Start) = println(s"$msg: time ${now()-from.start}")

  case class Start(start: Long = now())
}
