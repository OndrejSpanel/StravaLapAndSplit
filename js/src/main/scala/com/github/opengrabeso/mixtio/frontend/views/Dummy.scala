package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash._
import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
import io.udash.component.ComponentId
import io.udash.css._

import frontend.routing._
import shared.css._

import scala.concurrent.{ExecutionContext, Future}

object Dummy {

  /** The form's model structure. */
  case class PageModel(dummy: String)
  object PageModel extends HasModelPropertyCreator[PageModel]

  /** Contains the business logic of this view. */
  class PagePresenter(
    model: ModelProperty[PageModel],
    application: Application[RoutingState]
  )(implicit ec: ExecutionContext) extends Presenter[DummyPageState.type] {


    /** We don't need any initialization, so it's empty. */
    override def handleState(state: DummyPageState.type): Unit = {
    }

    def gotoAbout() = {
      application.goTo(AboutPageState)

    }
  }

  class PageView(
    model: ModelProperty[PageModel],
    presenter: PagePresenter,
  ) extends FinalView with CssView {

    import scalatags.JsDom.all._

    // Button from Udash Bootstrap wrapper
    private val submitButton = UdashButton(
      buttonStyle = ButtonStyle.Primary,
      block = true, componentId = ComponentId("about")
    )("Submit")

    submitButton.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        println("Dummy submit pressed")
        presenter.gotoAbout()
    }

    def getTemplate: Modifier = div(
      AboutPageStyles.container,
      div(
        p("I am dummy")
      ),
      submitButton.render
    )
  }

  /** Prepares model, view and presenter for demo view. */
  class PageViewFactory(
    application: Application[RoutingState],
  ) extends ViewFactory[DummyPageState.type] {

    import scala.concurrent.ExecutionContext.Implicits.global

    override def create(): (View, Presenter[DummyPageState.type]) = {
      // Main model of the view
      val model = ModelProperty(
        PageModel("dummy me")
      )

      val presenter = new PagePresenter(model, application)
      val view = new PageView(model, presenter)
      (view, presenter)
    }

    private object NonEmptyStringValidator extends Validator[String] {
      override def apply(element: String): Future[ValidationResult] = Future.successful {
        if (element.nonEmpty) Valid else Invalid("") // we can ignore error msg, because we don't display it anyway
      }
    }

  }

}