package com.github.opengrabeso.mixtio
package frontend
package views.settings

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.table.UdashTable
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.component.ComponentId
import io.udash.css._


class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView {
  val s = AboutPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  buttonOnClick(submitButton){presenter.gotoAbout()}

  def showWhenLoaded[T](property: ReadableProperty[T]) = {
    showIfElse(model.subProp(_.loading))(
      Seq(
        span("...").render
      ),
      Seq(
        span(bind(property)).render
      )
    )
  }
  def getTemplate: Modifier = {

    div(
      s.container,
      /*
      div(Grid.row)(
        div(Grid.col)(uploadButton.render),
        div(Grid.col)(stagingButton.render),
        div(Grid.col)(settingsButton.render),
      ),

       */

      div(
        "Settings",
        p("MaxHR: ", showWhenLoaded(model.subProp(_.settings).transform(_.maxHR))).render,
        p("elevFilter: ", showWhenLoaded(model.subProp(_.settings).transform(_.elevFilter))).render,
        p("questTimeOffset: ", showWhenLoaded(model.subProp(_.settings).transform(_.questTimeOffset))).render
      ),
      submitButton.render
    )
  }
}