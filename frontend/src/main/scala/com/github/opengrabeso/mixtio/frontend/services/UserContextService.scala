package com.github.opengrabeso.mixtio
package frontend
package services

import java.time.{ZoneOffset, ZonedDateTime}

import common.model._
import common.Util._

import scala.concurrent.{ExecutionContext, Future}
import UserContextService._

object UserContextService {
  final val normalCount = 15

  case class LoadedActivities(staged: Seq[ActivityHeader], strava: Seq[ActivityHeader])

  class UserContextData(userId: String, authCode: String, rpc: rest.RestAPI)(implicit ec: ExecutionContext) {
    var loaded = Option.empty[(Boolean, Future[LoadedActivities])]
    var context = UserContext(userId, authCode)

    def userAPI: rest.UserRestAPI = rpc.userAPI(context.userId, context.authCode)

    private def notBeforeByStrava(showAll: Boolean, stravaActivities: Seq[ActivityHeader]): ZonedDateTime = {
      if (showAll) ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC) minusMonths 24
      else stravaActivities.map(a => a.id.startTime).min
    }

    private def doLoadActivities(showAll: Boolean): Future[LoadedActivities] = {
      println(s"loadActivities showAll=$showAll")

      userAPI.lastStravaActivities(normalCount * 2).flatMap { allActivities =>
        val stravaActivities = allActivities.take(normalCount)
        val notBefore = notBeforeByStrava(showAll, stravaActivities)

        val ret = userAPI.stagedActivities(notBefore).map { stagedActivities =>
          LoadedActivities(stagedActivities, allActivities)
        }
        loaded = Some(showAll, ret)
        ret
      }
    }

    def loadCached(level: Boolean): Future[LoadedActivities] = {
      if (loaded.isEmpty || loaded.exists(!_._1 && level)) {
        doLoadActivities(level)
      } else {
        loaded.get._2
      }
    }
  }
}

class UserContextService(rpc: rest.RestAPI)(implicit ec: ExecutionContext) {

  private var userData: Option[UserContextData] = None

  def login(userId: String, authCode: String): UserContext = {
    val ctx = new UserContextData(userId, authCode, rpc)
    userData = Some(ctx)
    ctx.context
  }
  def logout(): Future[UserContext] = {
    userData.flatMap { ctx =>
      api.map(_.logout.map(_ => ctx.context))
    }.getOrElse(Future.failed(new UnsupportedOperationException))
  }

  def userName: Option[Future[String]] = api.map(_.name)
  def userId: Option[String] = userData.map(_.context.userId)

  def loadCached(level: Boolean): Future[LoadedActivities] = {
    userData.get.loadCached(level)
  }


  // TODO: double check authCode usage is safe here (it should be, we are frontend only here)
  def api: Option[rest.UserRestAPI] = userData.map { data =>
    rpc.userAPI(data.context.userId, data.context.authCode)
  }
}