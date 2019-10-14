package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash.bindings.inputs.{Checkbox, InputBinding}
import io.udash.bootstrap.form.UdashInputGroup
import io.udash._
import io.udash.bootstrap.button.UdashButton
import common.css._
import io.udash.bootstrap._
import BootstrapStyles._
import io.udash.css.CssView
import scalatags.JsDom.all._

trait PageUtils extends common.Formatting with CssView {
  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  def checkbox(p: Property[Boolean]): UdashInputGroup = {
    UdashInputGroup()(
      UdashInputGroup.appendCheckbox(Checkbox(p)())
    )
  }

  def button(disabled: ReadableProperty[Boolean], buttonText: ReadableProperty[String]): UdashButton = {
    UdashButton(disabled = disabled) { _ => Seq[Modifier](
      bind(buttonText),
      Spacing.margin(size = SpacingSize.Small)
    )}
  }

}
