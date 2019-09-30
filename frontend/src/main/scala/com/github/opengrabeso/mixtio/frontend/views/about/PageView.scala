package com.github.opengrabeso.mixtio
package frontend
package views
package about

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
) extends FinalView with CssView {
  val s = AboutPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")
  private val uploadButton = UdashButton(componentId = ComponentId("upload"))(_ => "Upload activity data...")
  private val stagingButton = UdashButton(componentId = ComponentId("staged"))(_ => "View all staged activities")
  private val settingsButton = UdashButton(componentId = ComponentId("settings"))(_ => "Settings")

  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  buttonOnClick(submitButton){presenter.gotoDummy()}
  buttonOnClick(settingsButton){presenter.gotoSettings()}

  def getTemplate: Modifier = {

    case class DisplayAttrib(name: String, value: ActivityIdModel => String, shortName: Option[String] = None)
    val attribs = Seq(
      DisplayAttrib("Time", _.startTime.toString),
      DisplayAttrib("Type", _.sportName),
      DisplayAttrib("Distance", _.distance.toString),
      DisplayAttrib("Duration", _ => ""),
      DisplayAttrib("Corresponding Strava activity", _ => "", Some("Strava")),
      DisplayAttrib("Data", _ => ""),
      DisplayAttrib("Source", _.id),
    )

    val striped = Property(true)
    val bordered = Property(true)
    val hover = Property(true)
    val small = Property(false)

    val table = UdashTable(model.subSeq(_.activities), striped = striped, bordered = bordered, hover = hover, small = small)(
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
        produce(el)(m => attribs.flatMap(a => td(a.value(m)).render))
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