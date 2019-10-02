package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash.bootstrap.button.UdashButton

trait PageUtils extends common.Formatting {
  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

}
