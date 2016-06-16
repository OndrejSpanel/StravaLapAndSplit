package com.github.opengrabeso.stravalas

object Main {
  def someComputation: String = "I have computed this!"

  def doComputation(i: Int): String = ("*" + i.toString) * 2

  def getLapsFrom(id: String): Array[Double] = {
    Array(0.0, 0.5, 1.0)
  }
}
