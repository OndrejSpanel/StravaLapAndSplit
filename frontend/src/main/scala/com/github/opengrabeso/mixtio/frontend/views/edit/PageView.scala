package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import facade.mapboxgl
import common.Formatting
import common.css._
import common.model._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.UdashForm
import io.udash.bootstrap.table.UdashTable
import io.udash.component.ComponentId
import io.udash.css._
import model.EditEvent
import scalatags.JsDom.all._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView {
  val s = EditPageStyles

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  buttonOnClick(submitButton){presenter.gotoSelect()}

  model.subProp(_.routeJS).listen {
    _.foreach { geojson =>
      // events should always be ready before the route
      val events = model.subProp(_.events).get
      val map = MapboxMap.display(geojson, events)

      model.subProp(_.events).listen { e =>
        // TODO: reset the map even data
        MapboxMap.changeEvents(map, e, model.subProp(_.routeJS).get.get)
      }

    }
  }

  def getTemplate: Modifier = {

    // value is a callback
    type EditAttrib = TableFactory.TableAttrib[EditEvent]
    val EditAttrib = TableFactory.TableAttrib

    //case class EditEvent(action: String, time: Int, km: Double, originalAction: String)
    val attribs = Seq[EditAttrib](
      EditAttrib("Action", (e, _, _) => EventView.eventDescription(e)),
      EditAttrib("Time", (e, _, _) => Formatting.displaySeconds(e.time).render),
      EditAttrib("Distance", (e, _, _) => Formatting.displayDistance(e.time).render),
      EditAttrib("Event", { (e, model, _) =>
        UdashForm() { factory =>
          factory.input.formGroup()(
            input = _ => factory.input.textInput(model.subProp(_.action))().render
          )
        }.render
      }),
    )

    val table = UdashTable(model.subSeq(_.events), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(TableFactory.headerFactory(attribs )),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    div(
      s.container,

      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            table.render,
          ).render
        )
      ),

      div(
        s.map,
        id := "map"
      )

    )
  }
}
