package com.github.opengrabeso.mixtio
package frontend
package views
package about

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
  val s = AboutPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")
  private val uploadButton = UdashButton(componentId = ComponentId("upload"))(_ => "Upload activity data...")
  private val stagingButton = UdashButton(componentId = ComponentId("staged"))(_ => "View all staged activities")
  private val settingsButton = UdashButton(componentId = ComponentId("settings"))(_ => "Settings")

  buttonOnClick(submitButton){presenter.gotoDummy()}
  buttonOnClick(settingsButton){presenter.gotoSettings()}

  def getTemplate: Modifier = {

    case class DisplayAttrib(name: String, value: (ActivityHeader, Option[ActivityId]) => Seq[Node], shortName: Option[String] = None)
    val attribs = Seq(
      DisplayAttrib("Time", (h, a) => displayTimeRange(h.id.startTime, h.id.endTime).render),
      DisplayAttrib("Type", (h, a) => h.id.sportName.toString.render),
      DisplayAttrib("Distance", (h, a) => displayDistance(h.id.distance).render),
      DisplayAttrib("Duration", (h, a) => displaySeconds(ChronoUnit.SECONDS.between(h.id.startTime, h.id.endTime).toInt).render),
      DisplayAttrib("Corresponding Strava activity", (h, a) => a.map(i => hrefLink(i).render).toSeq, Some("Strava")),
      DisplayAttrib("Data", (h, a) => h.describeData.render),
      DisplayAttrib("Source", (h, a) => h.id.id.toReadableString.render),
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
        produce(el)(ha => attribs.flatMap(a => td(a.value(ha._1, ha._2)).render))
      ).render
    )

    div(
      s.container,
      div(Grid.row)(
        div(Grid.col)(uploadButton.render),
        div(Grid.col)(stagingButton.render),
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