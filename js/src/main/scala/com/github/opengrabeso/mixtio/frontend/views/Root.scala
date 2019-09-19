package com.github.opengrabeso.mixtio
package frontend
package views

import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
import io.udash.component.ComponentId
import io.udash.css._
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Root {

  case class PageModel(athleteName: String, userId: String, waitingForLogin: Boolean)

  object PageModel extends HasModelPropertyCreator[PageModel]

  class PagePresenter(
    model: ModelProperty[PageModel],
    userContextService: services.UserContextService,
    application: Application[RoutingState]
  )(implicit ec: ExecutionContext) extends Presenter[RootState.type] {
    // start the login
    login()

    override def handleState(state: RootState.type): Unit = {}

    def login() = {
      val globalUserId = facade.UdashApp.currentUserId.orNull
      userContextService.login(globalUserId).map { ctx =>
        model.subProp(_.athleteName).set(ctx.name)
        model.subProp(_.userId).set(ctx.userId)
        model.subProp(_.waitingForLogin).set(false)
        println(s"Login completed for ${ctx.name} (${ctx.userId})")
      }.failed.foreach { _ =>
        println(s"Login failed")
        model.subProp(_.waitingForLogin).set(false)
      }
    }

    def logout() = ???
  }


  class View(model: ModelProperty[PageModel], presenter: PagePresenter) extends ContainerView with CssView {

    import scalatags.JsDom.all._

    private val logoutButton = UdashButton(
      buttonStyle = ButtonStyle.Default,
      block = true, componentId = ComponentId("logout-button")
    )("Log Out")

    logoutButton.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        println("Logout submit pressed")
        presenter.logout()
    }


    val header: Seq[HTMLElement] = {
      val name = model.subProp(_.athleteName)
      val userId = model.subProp(_.userId)

      Seq(
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

                logoutButton.render
              )
            )
          )
        ).render,
        p().render
      )
    }


    val footer: Seq[HTMLElement] = Seq(
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

    // ContainerView contains default implementation of child view rendering
    // It puts child view into `childViewContainer`
    override def getTemplate: Modifier = div(
      // loads Bootstrap and FontAwesome styles from CDN
      UdashBootstrap.loadBootstrapStyles(),
      UdashBootstrap.loadFontAwesome(),

      BootstrapStyles.container,
      header,
      childViewContainer,
      footer
    )
  }

  class PageViewFactory(
    application: Application[RoutingState],
    userService: services.UserContextService,
  ) extends ViewFactory[RootState.type] {

    import scala.concurrent.ExecutionContext.Implicits.global

    override def create(): (View, Presenter[RootState.type]) = {
      val model = ModelProperty(PageModel(userService.userName.orNull, userService.userId.orNull, false))
      val presenter = new PagePresenter(model, userService, application)

      val view = new View(model, presenter)
      (view, presenter)
    }
  }

}