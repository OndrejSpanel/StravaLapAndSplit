package com.github.opengrabeso.mixtio
package frontend
package views.about

import io.udash._

/** The form's model structure. */
case class AboutPageModel(about: String)
object AboutPageModel extends HasModelPropertyCreator[AboutPageModel]
