package com.github.opengrabeso.mixtio
package frontend
package views
package push

import io.udash._

case class PageModel(s: settings_base.SettingsModel, pending: Seq[String])

object PageModel extends HasModelPropertyCreator[PageModel]
