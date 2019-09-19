package com.github.opengrabeso.mixtio
package frontend
package views.about

import java.time.ZonedDateTime

import com.github.opengrabeso.mixtio.rest
import routing._
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class AboutPageViewFactory(
  application: Application[RoutingState],
) extends ViewFactory[AboutPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[AboutPageState.type]) = {
    // TODO: do not switch to view until the API has returned
    val model = ModelProperty(
      AboutPageModel(true, Seq())
    )

    for (userAPI <- facade.UdashApp.currentUserId.toOption.map(rest.RestAPIClient.api.userAPI)) {
      userAPI.lastStravaActivities(15).foreach { stravaActivities =>
        // TODO: make Util global and working with java.time
        println("lastStravaActivities received")
        implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = (x: ZonedDateTime, y: ZonedDateTime) => x.compareTo(y)
        val notBefore = stravaActivities.map { a =>
          ZonedDateTime.parse(a.startTime)
        }.min
        println(s"notBefore $notBefore")
        userAPI.stagedActivities(notBefore).foreach { storedActivities =>

          val ret = (stravaActivities ++ storedActivities).sortBy(_.id)
          model.subProp(_.activities).set(ret)
          model.subProp(_.loading).set(false)
        }
      }
    }

    val presenter = new AboutPagePresenter(model, application)
    val view = new AboutPageView(model, presenter)
    (view, presenter)
  }
}