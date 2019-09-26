package com.github.opengrabeso.mixtio
package frontend
package views
package about

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
import io.udash.bootstrap.table.UdashTable
import io.udash.component.ComponentId
import io.udash.css._

class AboutPageView(
  model: ModelProperty[AboutPageModel],
  presenter: AboutPagePresenter,
) extends FinalView with CssView {
  val s = AboutPageStyles

  import scalatags.JsDom.all._

  // Button from Udash Bootstrap wrapper
  private val submitButton = UdashButton(
    buttonStyle = ButtonStyle.Primary,
    block = true, componentId = ComponentId("about")
  )("Submit")

  submitButton.listen {
    case UdashButton.ButtonClickEvent(_, _) =>
      println("About submit pressed")
      presenter.gotoDummy()
  }

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

    val table = UdashTable(striped, bordered, hover, small)(model.subSeq(_.activities))(
      headerFactory = Some(() => tr {
        attribs.flatMap { a =>
          a.shortName.map(shortName =>
            Seq(
              td(s.wideMedia, b(a.name)).render,
              td(s.narrowMedia, b(shortName)).render
            )
          ).getOrElse(Seq(th(b(a.name)).render))
        }
      }.render),
      rowFactory = el => tr(
        produce(el)(m => attribs.flatMap(a => td(a.value(m)).render))
      ).render
    )

    div(
      s.container,
      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          table.render
        )
      ),
      submitButton.render
    )
  }
}