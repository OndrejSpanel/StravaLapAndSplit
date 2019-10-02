package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash.bindings.inputs.{Checkbox, InputBinding}
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.UdashInputGroup
import io.udash.properties.single.Property

trait PageUtils extends common.Formatting {
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

}
