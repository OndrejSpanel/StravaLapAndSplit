package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import java.time.{ZoneId, ZonedDateTime}

import common.model._
import common.css._
import io.udash._
import io.udash.bindings.modifiers.Binding.NestedInterceptor
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.{UdashForm, UdashInputGroup}
import io.udash.bootstrap.table.UdashTable
import io.udash.component.ComponentId
import io.udash.css._
import org.scalajs.dom.Node


class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView {
  val s = SelectPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  buttonOnClick(submitButton){presenter.gotoSelect()}

  def getTemplate: Modifier = {

    // value is a callback
    case class EditAttrib(name: String, value: (EditEvent, ModelProperty[EditEvent], NestedInterceptor) => Seq[Node], shortName: Option[String] = None)

    val attribs = Seq(
      EditAttrib("Time", (_, _, _) => "00:00".render),
      EditAttrib("Distance", (_, _, _) => "0.00".render)
    )

    val table = UdashTable(model.subSeq(_.events), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(_ => tr {
        attribs.flatMap { a =>
          a.shortName.map { shortName =>
            val wide = td(s.wideMedia, b(a.name)).render
            if (shortName.isEmpty) {
              Seq(wide)
            } else {
              val narrow = td(s.narrowMedia, b(a.shortName)).render
              Seq(wide, narrow)
            }
          }.getOrElse(Seq(th(b(a.name)).render))
        }
      }.render),
      rowFactory = (el,_) => tr(
        produceWithNested(el) { (ha, nested) =>
          attribs.flatMap { a =>
            if (a.shortName.contains("")) {
              td(s.wideMedia, a.value(ha, el.asModel, nested)).render
            } else {
              td(a.value(ha, el.asModel, nested)).render
            }
          }
        }
      ).render
    )

    div(
      s.container,s.limitWidth,

      div(
        repeat(model.subSeq(_.activities))(a => p(a.get.toString).render)
      ),

      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            table.render,
          ).render
        )
      )
    )
  }
}