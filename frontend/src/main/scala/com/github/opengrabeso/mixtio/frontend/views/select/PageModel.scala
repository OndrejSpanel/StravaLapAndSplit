package com.github.opengrabeso.mixtio
package frontend
package views.select

import io.udash._
import common.model._

/** The form's model structure. */
case class PageModel(loading: Boolean, activities: Seq[ActivityRow], error: Option[Throwable] = None)
object PageModel extends HasModelPropertyCreator[PageModel]
