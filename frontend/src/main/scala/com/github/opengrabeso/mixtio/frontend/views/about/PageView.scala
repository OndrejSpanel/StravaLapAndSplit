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

    case class DisplayAttrib(name: String, value: (ActivityHeader, Option[ActivityId]) => String, shortName: Option[String] = None)
    val attribs = Seq(
      DisplayAttrib("Time", (h, a) => displayTimeRange(h.id.startTime, h.id.endTime)),
      DisplayAttrib("Type", (h, a) => h.id.sportName.toString),
      DisplayAttrib("Distance", (h, a) => displayDistance(h.id.distance)),
      DisplayAttrib("Duration", (h, a) => displaySeconds(ChronoUnit.SECONDS.between(h.id.startTime, h.id.endTime).toInt)),
      DisplayAttrib("Corresponding Strava activity", (h, a) => a.map(_.name).orNull, Some("Strava")),
      DisplayAttrib("Data", (h, a) => h.describeData),
      DisplayAttrib("Source", (h, a) => h.id.id.toReadableString),
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