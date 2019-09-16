package com.github.opengrabeso.mixtio
package frontend
package views.about

import io.udash._
import io.udash.i18n.TranslationKey0

/** The form's model structure. */
case class AboutPageModel(waitingForResponse: Boolean, errors: Seq[String])
object AboutPageModel extends HasModelPropertyCreator[AboutPageModel]
