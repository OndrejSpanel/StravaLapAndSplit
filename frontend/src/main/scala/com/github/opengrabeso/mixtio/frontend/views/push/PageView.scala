package com.github.opengrabeso.mixtio
package frontend
package views
package push

import java.time.{ZoneId, ZonedDateTime}

import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.{UdashForm, UdashInputGroup}
import io.udash.component.ComponentId
import io.udash.css._


class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils {
  val s = SelectPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  buttonOnClick(submitButton){presenter.gotoSelect()}

  def getTemplate: Modifier = {

    // TODO: use DataStreamGPS.FilterSettings
    val elevFilterLabel = Array(
      "None", "Weak", "Normal", "Strong"
    )

    def transformTime(time: ZonedDateTime): String = {
      import TimeFormatting._
      val js = time.toJSDate
      formatTimeHMS(js)
    }

    div(
      s.container,s.limitWidth,

      div(
        UdashForm(inputValidationTrigger = UdashForm.ValidationTrigger.OnChange)(factory => Seq[Modifier](
          h1("Sending activities..."),
          factory.input.formGroup()(
            input = _ => factory.input.numberInput(model.subProp(_.settings.maxHR).transform(_.toString, _.toInt))().render,
            labelContent = Some(_ => "Max HR": Modifier),
            helpText = Some(_ => "Drop any samples with HR above this limit as erratic": Modifier)
          ),
          factory.input.formGroup()(
            input = _ => factory.input.numberInput(model.subProp(_.settings.questTimeOffset).transform(_.toString, _.toInt))().render,
            labelContent = Some(_ => "Additional sensor (e.g. Quest) time offset: ": Modifier),
            helpText = Some(_ => "Adjust up or down so that the time below matches the time on your watch/sensor": Modifier)
          ),
          p(
            "Current time: ",
            bind(model.subProp(_.currentTime).transform(transformTime))
          ),
          p {
            val questTime = (model.subProp(_.currentTime) combine model.subProp(_.settings.questTimeOffset))(_ plusSeconds _)
            b(
              "Sensor time: ",
              bind(questTime.transform(transformTime))
            )
          },
          factory.input.formGroup()(
            input = _ => factory.input.radioButtons(
              selectedItem = model.subProp(_.settings.elevFilter),
              options = elevFilterLabel.indices.toSeqProperty,
              inline = true.toProperty,
              validationTrigger = UdashForm.ValidationTrigger.None
            )(
              labelContent = (item, _, _) => Some(label(elevFilterLabel(item)))
            ).render,
            labelContent = Some(_ => "Elevation filter:": Modifier),
            helpText = Some(_ => "Elevation data smoothing": Modifier)
          ),
          hr(),
          produce(model.subSeq(_.pending)) { pending =>
            if (pending.nonEmpty) {
              pending.map(file =>
                p(file).render
              )
            } else {
              submitButton.render
            }
          }
        ))
      ),

    )
  }
}