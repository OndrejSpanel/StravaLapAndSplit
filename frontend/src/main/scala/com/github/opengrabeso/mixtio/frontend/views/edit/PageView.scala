package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import common.Formatting
import common.model._
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.table.UdashTable
import io.udash.component.ComponentId
import io.udash.css._

import io.udash.bootstrap._
import BootstrapStyles._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView {
  val s = SelectPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  def buttonOnClick(button: UdashButton)(callback: => Unit): Unit = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
  }

  buttonOnClick(submitButton){presenter.gotoSelect()}

  def getTemplate: Modifier = {

    // value is a callback
    type EditAttrib = TableFactory.TableAttrib[EditEvent]
    val EditAttrib = TableFactory.TableAttrib

    //case class EditEvent(action: String, time: Int, km: Double, originalAction: String)
    val attribs = Seq[EditAttrib](
      EditAttrib("Action", (e, _, _) => e.action.render),
      EditAttrib("Time", (e, _, _) => Formatting.displaySeconds(e.time).render),
      EditAttrib("Distance", (e, _, _) => Formatting.displayDistance(e.time).render),
      EditAttrib("Event", (e, _, _) => e.originalAction.render),
    )

    val table = UdashTable(model.subSeq(_.events), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(TableFactory.headerFactory(attribs )),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    div(
      s.container,s.limitWidth,

      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            table.render,
          ).render
        )
      ),

      div(
        Display.flex(),
        s.map,
        id := "map",
        `class` := "map clearfix",
        script(
          //language=JavaScript
          """
            mapboxgl.accessToken = 'pk.eyJ1Ijoib3NwYW5lbCIsImEiOiJjaXQwMXBqaGcwMDZ4MnpvM21ibzl2aGM5In0.1DeBqAQXvxLPajeeSK4jQQ';
            var map = new mapboxgl.Map({
              container: 'map', // container id
              style: 'mapbox://styles/mapbox/streets-v11', // stylesheet location
              center: [15, 42], // starting position [lng, lat]
              zoom: 9 // starting zoom
            });
            """
        )
      )

    )
  }
}