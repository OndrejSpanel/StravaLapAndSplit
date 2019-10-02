package com.github.opengrabeso.mixtio
package frontend
package views.settings

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.UdashInputGroup
import io.udash.bootstrap.table.UdashTable
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.component.ComponentId
import io.udash.css._
import scalacss.internal.ValueT.TypedAttr_MaxLength


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

  def showWhenLoaded(property: Property[Int], hint: String = "", maxChars: Int = 3) = {
    val stringProp: Property[String] = property.transform(_.toString, _.toInt)
    showIfElse(model.subProp(_.loading))(
      Seq(
        span("...").render
      ),
      Seq(
        UdashInputGroup.input(
          NumberInput(stringProp)(placeholder := hint, maxlength := maxChars).render
        ),
      )
    )
  }
  def getTemplate: Modifier = {

    div(Grid.row)(
      h1("Settings"),
      s.container,
      /*
      div(Grid.row)(
        div(Grid.col)(uploadButton.render),
        div(Grid.col)(stagingButton.render),
        div(Grid.col)(settingsButton.render),
      ),

       */

      UdashInputGroup()(
        div(Grid.col)(s.inputName, UdashInputGroup.prependText("Max HR: ")),
        div(Grid.col)(showWhenLoaded(model.subProp(_.settings.maxHR))),
        div(Grid.col)(s.inputDesc, UdashInputGroup.appendText("Drop any samples with HR above this limit as erratic"))
      ),UdashInputGroup()(
        div(Grid.col)(s.inputName, UdashInputGroup.prependText("Elevation filter: ")),
        div(Grid.col)(showWhenLoaded(model.subProp(_.settings.elevFilter))),
        div(Grid.col)(s.inputDesc, UdashInputGroup.appendText("Elevation filtering settings"))
      ),UdashInputGroup()(
        div(Grid.col)(s.inputName, UdashInputGroup.prependText("Additional sensor (e.g. Quest) time offset: ")),
        div(Grid.col)(showWhenLoaded(model.subProp(_.settings.questTimeOffset))),
        div(Grid.col)(s.inputDesc, UdashInputGroup.appendText("Adjust up or down so that Quest time below matches the time on your watch"))
      ),
      submitButton.render
    )
  }
}