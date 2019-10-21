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
import org.scalajs.dom.File

import scala.scalajs.js
import scala.util.{Failure, Success}

object PagePresenter {
  case class LoadedActivities(staged: Seq[ActivityHeader], strava: Seq[ActivityHeader])

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


  private def notBeforeByStrava(showAll: Boolean, stravaActivities: Seq[ActivityHeader]): ZonedDateTime = {
    if (showAll) ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC) minusMonths 24
    else stravaActivities.map(a => a.id.startTime).min
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

      def filterListed(activity: ActivityHeader, strava: Option[ActivityHeader]) = showAll || strava.isEmpty
      def findMatchingStrava(ids: Seq[ActivityHeader], strava: Seq[ActivityHeader]): Seq[(ActivityHeader, Option[ActivityHeader])] = {
        ids.map( a => a -> strava.find(_.id isMatching a.id))
      }

      val (stravaActivities, oldStravaActivities) = allStravaActivities.splitAt(normalCount)
      val neverBefore = alwaysIgnoreBefore(stravaActivities.map(_.id))

      // without "withZoneSameInstant" the resulting time contained strange [SYSTEM] zone suffix
      val notBefore = if (showAll) ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC) minusMonths 24
      else stravaActivities.map(a => a.id.startTime).min

      // never display any activity which should be cleaned by UserCleanup
      val oldStagedActivities = stagedActivities.filter(_.id.startTime < neverBefore)
      val toCleanup = findMatchingStrava(oldStagedActivities, oldStravaActivities).flatMap { case (k,v) => v.map(k -> _)}
      val recentActivities = (stagedActivities diff toCleanup.map(_._1)).filter(_.id.startTime >= notBefore).sortBy(_.id.startTime)

      val recentToStrava = findMatchingStrava(recentActivities, allStravaActivities).filter((filterListed _).tupled).map(a => (Some(a._1), a._2))

      // list Strava activities which have no Mixtio storage counterpart
      val stravaOnly = if (showAll) allStravaActivities.filterNot(a => recentActivities.exists(_.id.isMatchingExactly(a.id))).map(a => None -> Some(a)) else Nil

      val toShow = (recentToStrava ++ stravaOnly).sortBy(a => a._1.orElse(a._2).get.id.startTime)
      val mostRecentStrava = stravaActivities.headOption.map(_.id.startTime)

      model.subProp(_.activities).set(toShow.map { case (act, actStrava) =>

        val ignored = actStrava.isDefined || mostRecentStrava.exists(s => act.exists(s >= _.id.startTime))
        ActivityRow(act, actStrava, !ignored)
      })
      model.subProp(_.loading).set(false)
    }

  }

  override def handleState(state: SelectPageState.type): Unit = {}

  def unselectAll(): Unit = {
    model.subProp(_.activities).set {
      model.subProp(_.activities).get.map(_.copy(selected = false))
    }
  }

  private def selectedIds = {
    model.subProp(_.activities).get.filter(_.selected).flatMap(_.staged).map(_.id.id)
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
    uploads.startUpload(userService.api.get, selectedIds)
  }

  def uploadNewActivity() = {
    val selectedFiles = model.subSeq(_.uploads.selectedFiles).get

    val userId = userService.userId.get

    val uploader = new FileUploader(Url("/upload"))
    val uploadModel = uploader.upload("files", selectedFiles)
    uploadModel.listen(p => model.subProp(_.uploads.state).set(p))
  }

  def importFromStrava(act: ActivityHeader): Unit = {
    val stravaImport = act.id.id match {
      case FileId.StravaId(stravaId) =>
        // TODO: some progress indication
        userService.api.get.importFromStrava(stravaId)
      case _ =>
        Future.failed(new NoSuchElementException)
    }
    stravaImport.onComplete { i =>
      println(s"Strava ${act.id.id} imported as $i")
      model.subProp(_.activities).set {
        model.subProp(_.activities).get.map { a =>
          if (a.strava.contains(act)) {
            i match {
              case Success(actId) =>
                a.copy(staged = Some(act.copy(id = actId)))
              case Failure(ex) =>
                val Regex = "^HTTP ERROR (\\d+):.*".r.unanchored
                val message = ex.getMessage match {
                  case Regex(code) => s"HTTP Error $code" // provide short message for expected causes
                  case x => x // unexpected cause - provide full error
                }
                a.copy(downloadState = message)
            }
          } else a
        }
      }
    }
  }

  def mergeAndEdit(): Unit = {
    val selected = selectedIds
    application.goTo(EditPageState(selected))
  }

  def gotoSettings(): Unit = {
    application.goTo(SettingsPageState)
  }

  object uploads extends PendingUploads[FileId] {
    override def sendToStrava(fileIds: Seq[FileId]): Future[Seq[(FileId, String)]] = {
      userService.api.get.sendActivitiesToStrava(fileIds, facade.UdashApp.sessionId)
    }

    def modifyActivities(fileId: Set[FileId])(modify: ActivityRow => ActivityRow): Unit = {
      if (fileId.nonEmpty) model.subProp(_.activities).set {
        model.subProp(_.activities).get.map { a =>
          if (a.staged.exists(a => fileId.contains(a.id.id))) {
            modify(a)
          } else a
        }
      }
    }

    def setStravaFile(fileId: Set[FileId], stravaId: Option[FileId.StravaId]): Unit = {
      modifyActivities(fileId) { a =>
        a.copy(strava = stravaId.flatMap(s => a.staged.map(i => i.copy(id = i.id.copy(id = s)))))
      }
    }

    def setUploadProgressFile(fileId: Set[FileId], uploading: Boolean, uploadState: String): Unit = {
      modifyActivities(fileId) { a =>
        a.copy(uploading = uploading, uploadState = uploadState)
      }
    }
  }

}
