package com.github.opengrabeso.mixtio
package frontend
package views
package push

import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.component.ComponentId
import io.udash.css._
import scalatags.JsDom.all._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils with settings_base.SettingsView {
  val s = SelectPageStyles

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  buttonOnClick(submitButton){presenter.gotoSelect()}

  def getTemplate: Modifier = {

    div(
      s.container,s.limitWidth,
      template(model.subModel(_.s), presenter),
      hr(),
      produce(model.subSeq(_.pending)) { pending =>
        if (pending.nonEmpty) {
          pending.map(file =>
            p(file).render
          )
        } else {
          submitButton.render
        }
      }
    )
  }
}