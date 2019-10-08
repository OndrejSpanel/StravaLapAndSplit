package com.github.opengrabeso.mixtio
package frontend
package views
package select

import java.time.temporal.ChronoUnit

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.table.UdashTable
import io.udash.css._
import scalatags.JsDom.all._
import PageView._
import org.scalajs.dom.raw.HTMLElement
import scalatags.JsDom
object PageView {

  def hrefLink(ai: ActivityId): JsDom.TypedTag[HTMLElement] = {
    ai.id match {
      case FileId.StravaId(num) =>
        a(
          // TODO: CSS color "#FC4C02"
          href := s"https://www.strava.com/activities/$num",
          ai.shortName
        )
      case _ =>
        div(ai.id.toReadableString)
    }
  }
}

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles

  import scalatags.JsDom.all._

  private val uploadButton = UdashButton()(_ => "Upload activity data...")
  private val settingsButton = UdashButton()(_ => "Settings")

  // TODO: disable as needed
  def nothingSelected: ReadableProperty[Boolean] = {
    model.subProp(_.activities).transform(!_.exists(_.selected))
  }
  private def button(disabled: ReadableProperty[Boolean], buttonText: ReadableProperty[String]): UdashButton = {
    UdashButton(disabled = disabled) { _ => Seq[Modifier](
        bind(buttonText),
        Spacing.margin(size = SpacingSize.Small)
      )
    }
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
          // TODO: pass nested here?
          nested(checkbox(p.subProp(_.selected)))
        ).render
      ),
      TableFactory.TableAttrib("Time", (ar, _, _) => displayTimeRange(ar.staged.id.startTime, ar.staged.id.endTime).render),
      TableFactory.TableAttrib("Type", (ar, _, _) => ar.staged.id.sportName.toString.render),
      TableFactory.TableAttrib("Distance", (ar, _, _) => displayDistance(ar.staged.id.distance).render),
      TableFactory.TableAttrib("Duration", (ar, _, _) => displaySeconds(ChronoUnit.SECONDS.between(ar.staged.id.startTime, ar.staged.id.endTime).toInt).render),
      TableFactory.TableAttrib("Strava activity", { (ar, arProp, nested) => div {
        nested(showIfElse(arProp.subProp(_.uploading))(
          div(
            s.uploading,
            nested(bind(arProp.subProp(_.uploadState)))
          ).render,
          ar.strava.map(i => hrefLink(i).render).toSeq
        ))
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