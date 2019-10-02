package com.github.opengrabeso.mixtio
package frontend
package views.about

import java.time.ZonedDateTime

import routing._
import io.udash._
import common.model._
import common.Util._
import common.ActivityTime._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService
) extends ViewFactory[AboutPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global


  override def create(): (View, Presenter[AboutPageState.type]) = {
    // TODO: do not switch to view until the API has returned
    val model = ModelProperty(
      PageModel(true, Seq())
    )

    def findMatchingStrava(ids: Seq[ActivityHeader], strava: Seq[ActivityId]): Seq[(ActivityHeader, Option[ActivityId])] = {
      ids.map( a => a -> strava.find(_ isMatching a.id))
    }

    println(s"userService.api: ${userService.api}")
    for (userAPI <- userService.api) {

      val normalCount = 15
      def filterListed(activity: ActivityHeader, strava: Option[ActivityId]) = true

      userAPI.lastStravaActivities((normalCount * 2).toInt).map { allActivities =>

        val (stravaActivities, oldStravaActivities) = allActivities.splitAt(normalCount)

        // TODO: make Util global and working with java.time
        println("lastStravaActivities received")
        implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = (x: ZonedDateTime, y: ZonedDateTime) => x.compareTo(y)
        val notBefore = stravaActivities.map(a => a.startTime).min
        println(s"notBefore $notBefore")
        userAPI.stagedActivities(notBefore).foreach { stagedActivities =>

          val neverBefore = alwaysIgnoreBefore(stravaActivities)

          // never display any activity which should be cleaned by UserCleanup
          val oldStagedActivities = stagedActivities.filter(_.id.startTime < neverBefore)
          val toCleanup = findMatchingStrava(oldStagedActivities, oldStravaActivities).flatMap { case (k,v) => v.map(k -> _)}
          val recentActivities = (stagedActivities diff toCleanup.map(_._1)).filter(_.id.startTime >= notBefore).sortBy(_.id.startTime)

          val recentToStrava = findMatchingStrava(recentActivities, stravaActivities ++ oldStravaActivities).filter((filterListed _).tupled)

          model.subProp(_.activities).set(recentToStrava.map(t => ActivityRow(t._1, t._2)))
          model.subProp(_.loading).set(false)
        }
      }.failed.foreach { ex =>
        println(s"Failed $ex")
        model.subProp(_.loading).set(false)
      }
    }

    val presenter = new PagePresenter(model, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}