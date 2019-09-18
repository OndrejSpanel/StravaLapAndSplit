package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash.css._
import io.udash._

import io.udash.properties.single.Property
import scalatags.JsDom.all._
import org.scalajs.dom.raw.HTMLElement

package object fragment extends CssView {
  def header(name: Property[String], userId: Property[String]): Seq[HTMLElement] = Seq(
    div(
      id := "header",
      style := "background-color:#fca;overflow:auto", // TODO: CSS
      table(
        tbody(
          tr(
            td(a(href := "/", img(src := "static/stravaUpload64.png"))),
            td(
              table(
                tbody(
                  tr(td(a(href := "/", appName))),
                  tr(td(
                    "Athlete:",
                    produce(userId) { s =>
                      a(href := s"https://www.strava.com/athletes/$s", bind(name)).render
                    }
                  ))
                )
              )
            ),
            td(form(action := "logout", input(`type` := "submit", value := "Log Out")))
          )
        )
      )
    ).render,
    p().render
  )


  def footer: Seq[HTMLElement] = Seq(
    p().render,
    div(
      id := "footer",
      style := "background-color:#fca;overflow:auto", // TODO: move to a CSS
      a(
        href := "http://labs.strava.com/",
        id := "powered_by_strava",
        rel := "nofollow",
        img(
          attr("align") := "left",
          src :="static/api_logo_pwrdBy_strava_horiz_white.png",
          style := "max-height:46px" // TODO: move to a CSS
        )
      ),
      p(
        style := "color:#fff",  // TODO: move to a CSS
        a(
          href := "https://darksky.net/poweredby/",
          style := "color:#fff", // TODO: move to a CSS
          "Powered by Dark Sky"
        ),
        " © 2016 - 2018 ",
        a(
          href := s"https://github.com/OndrejSpanel/$gitHubName",
          style := "color:inherit",  // TODO: move to a CSS
        ),
        "Ondřej Španěl",
        div()
      )
    ).render
  )


}
