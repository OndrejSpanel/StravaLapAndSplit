package com.github.opengrabeso.mixtio
package frontend
package views
package select

import java.time.temporal.ChronoUnit

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.table.UdashTable
import io.udash.css._
import scalatags.JsDom.all._
import PageView._
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js

import scala.util.Try
import io.udash.bootstrap._
import BootstrapStyles._

object PageView {

  def hrefLink(ai: ActivityId): HTMLElement = {
    ai.id match {
      case FileId.StravaId(num) =>
        a(
          // TODO: CSS color "#FC4C02"
          href := s"https://www.strava.com/activities/$num",
          ai.shortName
        ).render
      case FileId.FilenameId(fileId) =>
        object IsInt {
          def unapply(x: String): Option[Int] = Try(x.toInt).toOption
        }
        // TODO: DRY with com.github.opengrabeso.mixtio.MoveslinkFiles.timestampFromName
        // GPS filename: Moveslink2/34FB984612000700-2017-05-23T16_27_11-0.sml
        val gpsPattern = "(.*)/.*-(\\d*)-(\\d*)-(\\d*)T(\\d*)_(\\d*)_(\\d*)-".r.unanchored
        // Quest filename Moveslink/Quest_2596420792_20170510143253.xml
        val questPattern = "(.*)/Quest_\\d*_(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)\\.".r.unanchored
        // note: may be different timezones, but a rough sort in enough for us (date is important)
        val specialCase = fileId match {
          case gpsPattern(folder, IsInt(yyyy), IsInt(mm), IsInt(dd), IsInt(h), IsInt(m), IsInt(s)) =>
            Some(folder, new js.Date(yyyy, mm - 1, dd, h, m, s)) // filename is a local time of the activity beginning
          case questPattern(folder, IsInt(yyyy), IsInt(mm), IsInt(dd), IsInt(h), IsInt(m), IsInt(s)) =>
            Some(folder, new js.Date(yyyy, mm - 1, dd, h, m, s)) // filename is a local time when the activity was downloaded
          case _ =>
            None
        }
        specialCase.map { case (folder, date) =>
          import TimeFormatting._
          span(
            title := fileId,
            b(folder),
            ": ",
            formatDateTime(date)
          ).render
        }.getOrElse(div(ai.id.toReadableString).render)

      case _ =>
        div(ai.id.toReadableString).render
    }
  }
}

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles

  private val uploadButton = UdashButton()(_ => "Upload activity data...")
  private val settingsButton = UdashButton()(_ => "Settings")

  def nothingSelected: ReadableProperty[Boolean] = {
    model.subProp(_.activities).transform(!_.exists(_.selected))
  }

  private val sendToStrava = button(nothingSelected, "Send to Strava".toProperty)
  private val deleteActivity = button(nothingSelected, s"Delete from $appName".toProperty)
  private val mergeAndEdit = button(
    nothingSelected,
    model.subProp(_.activities).transform(a => if (a.count(_.selected) > 1) "Merge and edit..." else "Edit...")
  )
  private val uncheckAll = button(nothingSelected, "Uncheck all".toProperty)

  private val filterCheckbox = Checkbox(model.subProp(_.showAll))()

  buttonOnClick(settingsButton){presenter.gotoSettings()}
  buttonOnClick(uncheckAll)(presenter.unselectAll())
  buttonOnClick(sendToStrava){presenter.sendSelectedToStrava()}
  buttonOnClick(mergeAndEdit){presenter.mergeAndEdit()}
  buttonOnClick(deleteActivity){presenter.deleteSelected()}

  def getTemplate: Modifier = {

    // value is a callback
    type DisplayAttrib = TableFactory.TableAttrib[ActivityRow]
    val attribs = Seq[DisplayAttrib](
      TableFactory.TableAttrib(
        "", (ar, p, nested) => div(
          nested(checkbox(p.subProp(_.selected)))
        ).render
      ),
      TableFactory.TableAttrib("Time", (ar, _, _) => displayTimeRange(ar.staged.id.startTime, ar.staged.id.endTime).render),
      TableFactory.TableAttrib("Type", (ar, _, _) => ar.staged.id.sportName.toString.render),
      TableFactory.TableAttrib("Distance", (ar, _, _) => displayDistance(ar.staged.id.distance).render),
      TableFactory.TableAttrib("Duration", (ar, _, _) => displaySeconds(ChronoUnit.SECONDS.between(ar.staged.id.startTime, ar.staged.id.endTime).toInt).render),
      TableFactory.TableAttrib("Strava activity", { (ar, arProp, nested) => div {
        // we are inside of `produce`, we can use `if` - `showIfElse` may have some performance advantage,
        // but this way it is easier to write and seems to work fine. Both `produce` and `showIfElse` are implemented
        // using `PropertyModifier`. The difference is `produce` is observing `activities` property, `showIfElse` could be more
        // granular, observing only `uploading` sub-property.
        // As `produce` must be called anyway because `activities` have changed, it should not matter.
        if (ar.uploading) {
          div(
            if (ar.uploadState != "") s.error else s.uploading,
            if (ar.uploadState != "") ar.uploadState else "Uploading..."
          ).render
        } else {
          ar.strava.map(i => hrefLink(i).render).toSeq
        }
      }.render}, Some("Strava")),
      TableFactory.TableAttrib("Data", (ar, _, _) => ar.staged.describeData.render),
      TableFactory.TableAttrib("Source", (ar, _, _) => hrefLink(ar.staged.id).render, Some("")),
    )

    val table = UdashTable(model.subSeq(_.activities), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(TableFactory.headerFactory(attribs)),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    div(
      s.container,
      div(Grid.row)(
        div(Grid.col)(uploadButton.render),
        div(Grid.col)(filterCheckbox.render, label("Show all": Modifier)),
        div(Grid.col)(settingsButton.render),
      ),

      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            bind(model.subProp(_.error).transform(_.map(ex => p(s"Error loading activities ${ex.toString}")).orNull)),
            table.render
          ).render
        )
      ),
      div(
        sendToStrava.render,
        mergeAndEdit.render,
        deleteActivity.render,
        uncheckAll.render
      )
    )
  }
}