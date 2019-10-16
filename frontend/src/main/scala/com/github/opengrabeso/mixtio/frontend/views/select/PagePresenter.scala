package com.github.opengrabeso.mixtio
package frontend
package views
package select

import java.time.{ZoneOffset, ZonedDateTime}

import common.model._
import common.Util._
import common.ActivityTime._
import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future, Promise}
import PagePresenter._

import scala.scalajs.js

object PagePresenter {
  case class LoadedActivities(staged: Seq[ActivityHeader], strava: Seq[ActivityId])

  // TODO: move to some Utils
  def delay(milliseconds: Int): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }
    p.future
  }

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

  private def selectedIds = {
    model.subProp(_.activities).get.filter(_.selected).map(_.staged.id.id)
  }

  def deleteSelected(): Unit = {
    val fileIds = selectedIds
    userService.api.get.deleteActivities(fileIds).foreach { _ =>
      model.subProp(_.activities).set {
        model.subProp(_.activities).get.filter(!_.selected)
      }
    }
  }

  def sendSelectedToStrava(): Unit = {
    val fileIds = selectedIds
    userService.api.get.sendActivitiesToStrava(fileIds, facade.UdashApp.sessionId).foreach { a =>
      val fileToPending = a.toMap
      // create upload indication for each activity being uploaded
      model.subProp(_.activities).set {
        model.subProp(_.activities).get.map { a => // : ActivityRow makes InteliJ happy
          val pendingId = fileToPending.get(a.staged.id.id)
          if (pendingId.isDefined) {
            a.copy(uploading = true, uploadState = "Uploading...")
          } else a
        }
      }
      uploads.addPending(userService.api.get, fileToPending)
    }
  }

  def mergeAndEdit(): Unit = {
    val selected = selectedIds
    application.goTo(EditPageState(selected))
  }

  def gotoSettings(): Unit = {
    application.goTo(SettingsPageState)
  }

  object uploads extends PendingUploads {
    def setStravaFile(fileId: Set[FileId], stravaId: Option[FileId.StravaId]): Unit = {
      model.subProp(_.activities).set {
        model.subProp(_.activities).get.map { a =>
          if (fileId contains a.staged.id.id) {
            a.copy(strava = stravaId.map(s => a.staged.id.copy(id = s)))
          } else a
        }
      }
    }

    def setUploadProgressFile(fileId: Set[FileId], uploading: Boolean, uploadState: String): Unit = {
      model.subProp(_.activities).set {
        model.subProp(_.activities).get.map { a =>
          if (fileId contains a.staged.id.id) {
            a.copy(uploading = uploading, uploadState = uploadState)
          } else a
        }
      }
    }
  }

}
