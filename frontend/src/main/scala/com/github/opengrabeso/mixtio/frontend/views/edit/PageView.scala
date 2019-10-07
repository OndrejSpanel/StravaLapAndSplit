package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import java.time.{ZoneId, ZonedDateTime}

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.{UdashForm, UdashInputGroup}
import io.udash.component.ComponentId
import io.udash.css._


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

    div(
      s.container,s.limitWidth,

      div(
        repeat(model.subSeq(_.activities))(a => p(a.get.toString).render)
      )
    )
  }
}