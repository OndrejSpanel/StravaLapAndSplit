package com.github.opengrabeso.mixtio
package frontend
package views.select

import java.time.{ZoneOffset, ZonedDateTime}

import common.model._
import common.Util._
import common.ActivityTime._
import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future}
import PagePresenter._

object PagePresenter {
  case class LoadedActivities(staged: Seq[ActivityHeader], strava: Seq[ActivityId])
}

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  userService: services.UserContextService
)(implicit ec: ExecutionContext) extends Presenter[SelectPageState.type] {
  model.subProp(_.showAll).listen { p =>
    loadActivities(p)
  }

  var loaded = Option.empty[(Boolean, Future[LoadedActivities])]

  final private val normalCount = 15


  private def notBeforeByStrava(showAll: Boolean, stravaActivities: Seq[ActivityId]): ZonedDateTime = {
    // without "withZoneSameInstant" the resulting time contained strange [SYSTEM] zone suffix
    if (showAll) ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC) minusMonths 24
    else stravaActivities.map(a => a.startTime).min
  }

  def doLoadActivities(showAll: Boolean): Future[LoadedActivities] = {
    println(s"loadActivities showAll=$showAll")
    model.subProp(_.loading).set(true)
    model.subProp(_.activities).set(Nil)

    userService.api match {
      case Some(userAPI) =>
        userAPI.lastStravaActivities(normalCount * 2).flatMap { allActivities =>
          val stravaActivities = allActivities.take(normalCount)
          val notBefore = notBeforeByStrava(showAll, stravaActivities)

          val ret = userAPI.stagedActivities(notBefore).map { stagedActivities =>
            LoadedActivities(stagedActivities, allActivities)
          }
          loaded = Some(showAll, ret)
          ret
        }
      case None =>
        Future.failed(new NoSuchElementException)

    }
  }

  private def loadCached(level: Boolean): Future[LoadedActivities] = {
    println(s"loadCached $level")
    if (loaded.isEmpty || loaded.exists(!_._1 && level)) {
      doLoadActivities(level)
    } else {
      loaded.get._2
    }
  }

  def loadActivities(showAll: Boolean) = {
    val load = loadCached(showAll)

    for (LoadedActivities(stagedActivities, allStravaActivities) <- load) {
      println(s"loadActivities loaded staged: ${stagedActivities.size}, Strava: ${allStravaActivities.size}")

      def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = showAll || strava.isEmpty
      def findMatchingStrava(ids: Seq[ActivityHeader], strava: Seq[ActivityId]): Seq[(ActivityHeader, Option[ActivityId])] = {
        ids.map( a => a -> strava.find(_ isMatching a.id))
      }

      val (stravaActivities, oldStravaActivities) = allStravaActivities.splitAt(normalCount)
      val neverBefore = alwaysIgnoreBefore(stravaActivities)

      // without "withZoneSameInstant" the resulting time contained strange [SYSTEM] zone suffix
      val notBefore = if (showAll) ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC) minusMonths 24
      else stravaActivities.map(a => a.startTime).min

      // never display any activity which should be cleaned by UserCleanup
      val oldStagedActivities = stagedActivities.filter(_.id.startTime < neverBefore)
      val toCleanup = findMatchingStrava(oldStagedActivities, oldStravaActivities).flatMap { case (k,v) => v.map(k -> _)}
      val recentActivities = (stagedActivities diff toCleanup.map(_._1)).filter(_.id.startTime >= notBefore).sortBy(_.id.startTime)

      val recentToStrava = findMatchingStrava(recentActivities, stravaActivities ++ oldStravaActivities).filter((filterListed _).tupled)

      model.subProp(_.activities).set(recentToStrava.map { case (act, actStrava) =>
        val mostRecentStrava = stravaActivities.headOption.map(_.startTime)

        val ignored = actStrava.isDefined || mostRecentStrava.exists(_ >= act.id.startTime)
        ActivityRow(act, actStrava, !ignored)
      })
      model.subProp(_.loading).set(false)
    }

  }



    override def handleState(state: SelectPageState.type): Unit = {
  }

  def unselectAll(): Unit = {
    model.subProp(_.activities).set {
      model.subProp(_.activities).get.map(_.copy(selected = false))
    }
  }

  def gotoSettings(): Unit = {
    application.goTo(SettingsPageState)
  }
}
