package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import common.Formatting
import common.css._
import io.udash._
import io.udash.bootstrap.button.{UdashButtonGroup, UdashButtonToolbar}
import io.udash.bootstrap.form.UdashForm
import io.udash.bootstrap.table.UdashTable
import io.udash.css._
import model.EditEvent
import scalatags.JsDom.all._
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.component.ComponentId

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils with ActivityLink {
  val s = EditPageStyles

  model.subProp(_.routeJS).listen {
    _.foreach { geojson =>
      // events should always be ready before the route
      val events = model.subProp(_.events).get
      val map = MapboxMap.display(geojson, events)

      model.subProp(_.events).listen { e =>
        MapboxMap.changeEvents(map, e, model.subProp(_.routeJS).get.get)
      }

    }
  }

  def getTemplate: Modifier = {
    import TimeFormatting._
    // value is a callback
    type EditAttrib = TableFactory.TableAttrib[EditEvent]
    val EditAttrib = TableFactory.TableAttrib

    //case class EditEvent(action: String, time: Int, km: Double, originalAction: String)
    val attribs = Seq[EditAttrib](
      EditAttrib("Action", (e, _, _) => EventView.eventDescription(e)),
      EditAttrib("Time", (e, _, _) => span(
        title := formatTimeHMS(e.event.stamp.toJSDate),
        Formatting.displaySeconds(e.time)
      ).render),
      EditAttrib("Distance", (e, _, _) => Formatting.displayDistance(e.dist).render),
      EditAttrib("Event", { (e, eModel, _) =>
        UdashForm() { factory =>
          factory.disabled(eModel.subProp(_.active).transform(!_)) { _ =>
            val possibleActions = e.event.listTypes.map(t => t.id -> t.display).toSeq
            val actionIds = possibleActions.map(_._1)
            val possibleActionsMap = possibleActions.toMap
            if (actionIds.size > 1) {
              factory.input.formGroup()(
                input = { _ =>
                  factory.input.select(
                    eModel.subProp(_.action), actionIds.toSeqProperty,
                    inputId = EventView.selectId(e.time)
                  )(id => span(possibleActionsMap(id))).render
                }
              )
            } else if (actionIds.nonEmpty) {
              span(possibleActions.head._2).render
            } else {
              span("").render
            }
          }
        }.render
      }),
      EditAttrib("", { (e, eModel, nested) =>
        import io.udash.bootstrap.utils.UdashIcons.FontAwesome.Solid
        import io.udash.bootstrap.utils.UdashIcons.FontAwesome.Modifiers
        def place[T](xs: T) = xs
        if (e.boundary || !e.active) {
          val disabled = eModel.subProp(_.active).transform(!_)
          val disabledDelete = eModel.transform(e => !e.boundary)
          div(
            Float.right(),
            UdashButtonToolbar()(
              if (e.boundary) {
                UdashButtonGroup()(
                  place(iconButton("Upload to Strava", disabled = disabled)(Modifiers.Sizing.xs, Solid.cloudUploadAlt)
                    .onClick(presenter.sendToStrava(e.time)).render),
                  place(iconButton("Download", disabled = disabled)(Modifiers.Sizing.xs, Solid.fileDownload)
                    .onClick(presenter.download(e.time)).render)
                ).render
              } else span().render,
              UdashButtonGroup()(
                place(iconButton("Delete", disabled = disabledDelete) (
                  Modifiers.Sizing.xs,
                  if (e.active) Solid.toggleOn else Solid.toggleOff
                ).onClick {
                  presenter.toggleSplitDisable(e.time)
                }.render)
              ).render
            ).render,
            if (e.uploading) {
              div(
                if (e.uploadState.nonEmpty) s.error else s.uploading,
                if (e.uploadState.nonEmpty) raw(e.uploadState) else "Uploading..."
              )
            } else {
              // TODO: get the resulting activity name from Strava
              e.strava.map { strava =>
                hrefLink(strava, strava.id.toString)
              }
            }
          ).render
        } else {
          div().render
        }
      })
    )

    val table = UdashTable(
      model.subSeq(_.events), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty,
      componentId = ComponentId("edit-table")
    )(
      headerFactory = Some(TableFactory.headerFactory(attribs )),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    div(
      s.container,

      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            table.render
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
