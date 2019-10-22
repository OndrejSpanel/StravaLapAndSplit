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
) extends FinalView with CssView with PageUtils with settings_base.SettingsView with ActivityLink {
  val s = SelectPageStyles
  val ss = SettingsPageStyles

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Proceed...")

  buttonOnClick(submitButton){presenter.gotoSelect()}

  def getTemplate: Modifier = {

    div(
      ss.flexContainer,
      div(
        ss.container,
        ss.flexItem,
        template(model.subModel(_.s), presenter),
        showIf(model.subSeq(_.pending).transform(_.isEmpty))(submitButton.render)
      ),
      produce(model.subSeq(_.pending)) { pending =>
        if (pending.nonEmpty) {
          div(
            ss.container,
            ss.flexItem,
            table(
              tr(th(h2("Uploading:"))),
              pending.map(file =>
                tr(td(niceFileName(file)))
              )
            )
          ).render
        } else {
          div().render
        }
      }
    )
  }
}