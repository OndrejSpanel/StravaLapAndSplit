package com.github.opengrabeso.mixtio
package frontend
package views

import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.bootstrap.button.UdashButton
import io.udash.component.ComponentId
import io.udash.css._
import common.css._
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

    def logout() = {
      if (!model.subProp(_.waitingForLogin).get && model.subProp(_.userId).get != null) {
        model.subProp(_.waitingForLogin).set(false)
        println("Start logout")
        val oldName = userContextService.userName
        val oldId = userContextService.userId
        userContextService.logout().andThen {
          case Success(_) =>
            println(s"Logout done for $oldName ($oldId)")
            model.subProp(_.athleteName).set(null)
            model.subProp(_.userId).set(null)
            model.subProp(_.waitingForLogin).set(false)
            facade.UdashApp.currentUserId = scalajs.js.undefined
          case Failure(_) =>
        }
      }
    }
  }


  class View(model: ModelProperty[PageModel], presenter: PagePresenter) extends ContainerView with CssView {

    import scalatags.JsDom.all._

    private val logoutButton = UdashButton(
      //buttonStyle = ButtonStyle.Default,
      block = true.toProperty, componentId = ComponentId("logout-button")
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
          GlobalStyles.header,
          id := "header",
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
        GlobalStyles.header,
        id := "footer",
        a(
          href := "http://labs.strava.com/",
          id := "powered_by_strava",
          rel := "nofollow",
          img(
            GlobalStyles.stravaImg,
            attr("align") := "left",
            src :="static/api_logo_pwrdBy_strava_horiz_white.png",
          )
        ),
        p(
          GlobalStyles.footerText,
          a(
            GlobalStyles.footerLink,
            href := "https://darksky.net/poweredby/",
            "Powered by Dark Sky"
          ),
          " © 2016 - 2018 ",
          a(
            href := s"https://github.com/OndrejSpanel/$gitHubName",
            GlobalStyles.footerLink,
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