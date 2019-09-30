package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash.bootstrap.button.UdashButton

trait PageUtils {
  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  def displayDistance(dist: Double): String = "%.2f km".format(dist*0.001)


}
