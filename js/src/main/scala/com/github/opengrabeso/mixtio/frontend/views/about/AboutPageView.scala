package com.github.opengrabeso.mixtio
package frontend
package views.about

import shared.css._
import io.udash._
import io.udash.bootstrap.alert.UdashAlert
import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
import io.udash.bootstrap.form.UdashForm
import io.udash.bootstrap.tooltip.UdashPopover
import io.udash.bootstrap.utils.UdashIcons.FontAwesome
import io.udash.component.ComponentId
import io.udash.css._
import io.udash.i18n._
import org.scalajs.dom.raw.Event

class AboutPageView(
  model: ModelProperty[AboutPageModel],
  presenter: AboutPagePresenter,
) extends FinalView with CssView {

  import scalatags.JsDom.all._

  private val errorsAlert = UdashAlert.danger(ComponentId("alerts"),
    repeat(model.subSeq(_.errors)) { error =>
      div(error.get).render
    }
  )

  private val infoIcon = span(
    AboutPageStyles.infoIcon,
    span(
      FontAwesome.Modifiers.stack,
      span(FontAwesome.info, FontAwesome.Modifiers.stack1x),
      span(FontAwesome.circleThin, FontAwesome.Modifiers.stack2x)
    )
  ).render

  def getTemplate: Modifier = div(
    AboutPageStyles.container,

    UdashForm().render
  )
}