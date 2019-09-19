package com.github.opengrabeso.mixtio
package frontend
package views.about

import io.udash._
import common.model._

/** The form's model structure. */
case class AboutPageModel(loading: Boolean, activities: Seq[ActivityIdModel])
object AboutPageModel extends HasModelPropertyCreator[AboutPageModel]
