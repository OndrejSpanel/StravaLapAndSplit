package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import common.Formatting
import common.css._
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
) extends FinalView with CssView with PageUtils {
  val s = EditPageStyles

  private val sendToStrava = button(nothingSelected, "Send selected to Strava".toProperty)
  private val downloadAsFiles = button(nothingSelected, "Download as files".toProperty)

  def nothingSelected: ReadableProperty[Boolean] = {
    model.subProp(_.events).transform(!_.exists(_.processed))
  }

  buttonOnClick(sendToStrava){presenter.sendToStrava()}
  buttonOnClick(downloadAsFiles){presenter.download()}

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
      EditAttrib("", (e, eModel, _) =>
        if (e.action.startsWith("split")) {
          UdashForm() { factory =>
            factory.input.formGroup()(
              input = _ => factory.input.checkbox(eModel.subProp(_.selected))().render
            )
          }.render
        } else {
          span().render
        }
      ),
      EditAttrib("Action", (e, _, _) => EventView.eventDescription(e)),
      EditAttrib("Time", (e, _, _) => Formatting.displaySeconds(e.time).render),
      EditAttrib("Distance", (e, _, _) => Formatting.displayDistance(e.time).render),
      EditAttrib("Event", { (e, eModel, _) =>
        UdashForm() { factory =>
          val possibleActions = e.event.listTypes.map(t => t.id -> t.display).toMap
          val actionIds = possibleActions.keys
          if (actionIds.size > 1) {
            factory.input.formGroup()(
              input = _ => factory.input.select(eModel.subProp(_.action), actionIds.toSeq.toSeqProperty)(id => span(possibleActions(id))).render
            )
          } else if (actionIds.nonEmpty) {
            span(possibleActions.head._2).render
          } else {
            span("").render
          }
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
            div(
              sendToStrava,
              downloadAsFiles
            )
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
