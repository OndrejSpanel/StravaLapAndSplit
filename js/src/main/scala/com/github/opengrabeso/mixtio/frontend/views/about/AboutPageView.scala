package com.github.opengrabeso.mixtio
package frontend
package views
package about

import common.css._
import io.udash._
import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
import io.udash.component.ComponentId
import io.udash.css._

class AboutPageView(
  model: ModelProperty[AboutPageModel],
  presenter: AboutPagePresenter,
) extends FinalView with CssView {

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

  def getTemplate: Modifier = div(
    AboutPageStyles.container,
    fragment.header(model.subProp(_.athleteName), model.subProp(_.userId)),
    div(
      p(
        "Athlete: ",
        bind(model.subProp(_.athleteName))
      ).render,
      p("Faster than a wind")
    ),
    submitButton.render,
    fragment.footer
  )
}