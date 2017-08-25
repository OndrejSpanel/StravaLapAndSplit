package com.github.opengrabeso.stravamat

import org.joda.time.{Seconds, DateTime => ZonedDateTime}

import scala.collection.immutable.SortedMap

object Function {
  type Func = List[(ZonedDateTime, Double)]

  object Window {
    def apply(durationSec: Double) = new Window(Vector.empty, 0)(durationSec)
  }
  case class Window(data: Vector[(ZonedDateTime, Double)], private val totalDistance: Double)(durationSec: Double) {
    def isEmpty = data.isEmpty
    def begTime = data.head._1
    def endTime = data.last._1
    def duration = Seconds.secondsBetween(begTime, endTime).getSeconds
    def distance = if (isEmpty) 0.0 else totalDistance - data.head._2 // first distance was before the first timestamp
    def speed = if (duration > 0) distance / duration else 0.0
    def keepSize = if (duration <= durationSec) this else Window(data.tail, totalDistance - data.head._2)(durationSec)
    def :+ (item: (ZonedDateTime, Double)) = Window(data :+ item, totalDistance + item._2)(durationSec)
  }


  @scala.annotation.tailrec
  private def smoothingRecurse(done: Func, prev: Window, todo: Func): Func = {
    if (todo.isEmpty) done
    else if (prev.isEmpty) {
      smoothingRecurse(todo.head +: done, prev :+ todo.head, todo.tail)
    } else {
      val newWindow = (prev :+ todo.head).keepSize
      val duration = newWindow.duration
      val windowSpeed = prev.speed
      val interval = Seconds.secondsBetween(prev.endTime, todo.head._1).getSeconds
      val smoothDist = if (duration + interval > 0) (windowSpeed * duration + todo.head._2) / (duration + interval) else 0
      smoothingRecurse((todo.head._1 -> smoothDist) +: done, newWindow, todo.tail)
    }
  }

  def smoothing(todo: Func, durationSec: Double): Func = {
    smoothingRecurse(Nil, Window(durationSec), todo).reverse
  }

  def selectAbove(f: Func, threshold: Double): List[(ZonedDateTime, Boolean)] = {
    f.map {case (t, ft) => t -> (ft > threshold)}
  }

  def toIntervals(f: Func, pred: Double => Boolean) = {

    @scala.annotation.tailrec
    def recurse(f: Func, done: List[(ZonedDateTime, ZonedDateTime)]): List[(ZonedDateTime, ZonedDateTime)] = {
      val (take, left) = f.span {case (t, ft) => pred(ft)}
      val (skip, todo) = left.span {case (t, ft) => !pred(ft)}

      if (take.nonEmpty) {
        recurse(todo, (take.head._1, take.last._1) :: done)
      } else if (skip.nonEmpty) {
        recurse(todo, done)
      } else done
    }

    recurse(f, Nil).reverse
  }

  def dropIntervals[T](stream: SortedMap[ZonedDateTime, T], toDropIntervals: List[(ZonedDateTime, ZonedDateTime)]): List[(ZonedDateTime, T)] = {
    @scala.annotation.tailrec
    def recurse(todoStream: SortedMap[ZonedDateTime, T], todo: List[(ZonedDateTime, ZonedDateTime)], done: List[(ZonedDateTime, T)]): List[(ZonedDateTime, T)] = {
      todo match {
        case head :: tail =>
          val before = todoStream.until(head._1 minusMillis 1) // plus / minus epsilon so that the range is excluded
          val after = todoStream.from(head._2 plusMillis 1)
          recurse(after, tail, before.toList.reverse ++ done)
        case _ =>
          todoStream.toList.reverse ++ done
      }
    }
    recurse(stream, toDropIntervals, Nil).reverse
  }

}
