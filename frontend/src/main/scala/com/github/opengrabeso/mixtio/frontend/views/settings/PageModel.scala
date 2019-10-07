package com.github.opengrabeso.mixtio
package frontend
package views.settings

import java.time.ZonedDateTime
import common.model._
import io.udash._

case class PageModel(loading: Boolean, settings: SettingsStorage, currentTime: ZonedDateTime)

object PageModel extends HasModelPropertyCreator[PageModel]
