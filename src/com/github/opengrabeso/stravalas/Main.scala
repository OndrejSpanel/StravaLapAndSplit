package com.github.opengrabeso.stravalas

object Main {
  def someComputation: String = "I have computed this!"

  def doComputation(i: Int): String = ("*" + i.toString) * 2

  def getLapsFrom(authToken: String, id: String): Array[Double] = {
    Array(0.0, 0.5, 1.0)
  }

  def secret: String = {
    val secretStream = Main.getClass.getResourceAsStream("/secret.txt")
    scala.io.Source.fromInputStream(secretStream).mkString
  }
}
