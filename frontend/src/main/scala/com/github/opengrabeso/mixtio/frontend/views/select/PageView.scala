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
import io.udash.component.ComponentId
import io.udash.css._
import scalatags.JsDom.all._
import PageView._
import io.udash.bindings.modifiers.Binding.NestedInterceptor
import org.scalajs.dom.Node
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

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")
  private val uploadButton = UdashButton(componentId = ComponentId("upload"))(_ => "Upload activity data...")
  private val settingsButton = UdashButton(componentId = ComponentId("settings"))(_ => "Settings")
  private val filterCheckbox = Checkbox(model.subProp(_.showOnlyRecent))()

  buttonOnClick(submitButton){presenter.gotoDummy()}
  buttonOnClick(settingsButton){presenter.gotoSettings()}

  def getTemplate: Modifier = {

    // value is a callback
    case class DisplayAttrib(name: String, value: (ActivityRow, ModelProperty[ActivityRow], NestedInterceptor) => Seq[Node], shortName: Option[String] = None)
    val attribs = Seq(
      DisplayAttrib(
        "", (ar, p, nested) => div(
          // TODO: pass nested here?
          nested(checkbox(p.subProp(_.selected)))
        ).render
      ),
      DisplayAttrib("Time", (ar, _, _) => displayTimeRange(ar.staged.id.startTime, ar.staged.id.endTime).render),
      DisplayAttrib("Type", (ar, _, _) => ar.staged.id.sportName.toString.render),
      DisplayAttrib("Distance", (ar, _, _) => displayDistance(ar.staged.id.distance).render),
      DisplayAttrib("Duration", (ar, _, _) => displaySeconds(ChronoUnit.SECONDS.between(ar.staged.id.startTime, ar.staged.id.endTime).toInt).render),
      DisplayAttrib("Strava activity", (ar, _, _) => ar.strava.map(i => hrefLink(i).render).toSeq, Some("Strava")),
      DisplayAttrib("Data", (ar, _, _) => ar.staged.describeData.render),
      DisplayAttrib("Source", (ar, _, _) => hrefLink(ar.staged.id).render),
    )

    val table = UdashTable(model.subSeq(_.activities), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(_ => tr {
        attribs.flatMap { a =>
          a.shortName.map(shortName =>
            Seq(
              td(s.wideMedia, b(a.name)).render,
              td(s.narrowMedia, b(shortName)).render
            )
          ).getOrElse(Seq(th(b(a.name)).render))
        }
      }.render),
      rowFactory = (el,_) => tr(
        produceWithNested(el) { (ha, nested) =>
          attribs.flatMap(a => td(a.value(ha, el.asModel, nested)).render)
        }
      ).render
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
      submitButton.render
    )
  }
}