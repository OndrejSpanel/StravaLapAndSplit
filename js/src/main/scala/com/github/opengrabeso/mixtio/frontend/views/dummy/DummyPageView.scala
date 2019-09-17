package com.github.opengrabeso.mixtio
package frontend
package views.dummy

import shared.css._
import io.udash._
import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
import io.udash.component.ComponentId
import io.udash.css._

class DummyPageView(
  model: ModelProperty[DummyPageModel],
  presenter: DummyPagePresenter,
) extends FinalView with CssView {

  import scalatags.JsDom.all._

  // Button from Udash Bootstrap wrapper
  private val submitButton = UdashButton(
    buttonStyle = ButtonStyle.Primary,
    block = true, componentId = ComponentId("about")
  )("Submit")

  submitButton.listen {
    case UdashButton.ButtonClickEvent(_, _) =>
      println("Dummy submit pressed")
      presenter.gotoAbout()
  }

  def getTemplate: Modifier = div(
    AboutPageStyles.container,
    div(
      p("I am dummy")
    ),
    submitButton.render
  )
}