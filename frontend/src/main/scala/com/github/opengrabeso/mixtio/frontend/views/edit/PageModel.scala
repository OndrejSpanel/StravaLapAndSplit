package com.github.opengrabeso.mixtio
package frontend
package views.edit

import java.time.ZonedDateTime
import common.model._
import io.udash._

case class PageModel(loading: Boolean, activities: Seq[FileId], merged: Option[FileId] = None, events: Seq[EditEvent] = Nil)

object PageModel extends HasModelPropertyCreator[PageModel]
