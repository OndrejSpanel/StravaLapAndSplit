package com.github.opengrabeso.mixtio
package frontend
package views.settings

import common.model._
import io.udash._

/** The form's model structure. */
case class PageModel(loading: Boolean, settings: SettingsStorage)

object PageModel extends HasModelPropertyCreator[PageModel]
