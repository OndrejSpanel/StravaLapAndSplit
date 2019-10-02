package com.github.opengrabeso.mixtio
package frontend
package views.about

import java.time.ZonedDateTime

import com.github.opengrabeso.mixtio.rest
import routing._
import io.udash._

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

    println(s"userService.api: ${userService.api}")
    for (userAPI <- userService.api) {
      userAPI.lastStravaActivities(15).map { stravaActivities =>
        // TODO: make Util global and working with java.time
        println("lastStravaActivities received")
        implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = (x: ZonedDateTime, y: ZonedDateTime) => x.compareTo(y)
        val notBefore = stravaActivities.map(a => a.startTime).min
        println(s"notBefore $notBefore")
        userAPI.stagedActivities(notBefore).foreach { storedActivities =>
          val ret = (stravaActivities ++ storedActivities.map(_.id)).sortBy(_.startTime)
          model.subProp(_.activities).set(ret)
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