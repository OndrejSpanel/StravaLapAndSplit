package com.github.opengrabeso.mixtio
package frontend
package views

import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.css._

import scala.concurrent.ExecutionContext

object Root {

  case class PageModel(athleteName: String, userId: String)

  object PageModel extends HasModelPropertyCreator[PageModel]


  class PagePresenter(
    model: ModelProperty[PageModel],
    application: Application[RoutingState]
  )(implicit ec: ExecutionContext) extends Presenter[RootState.type] {


    /** We don't need any initialization, so it's empty. */
    override def handleState(state: RootState.type): Unit = {}
  }


  class View(model: ModelProperty[PageModel], presenter: PagePresenter) extends ContainerView with CssView {

    import scalatags.JsDom.all._

    // ContainerView contains default implementation of child view rendering
    // It puts child view into `childViewContainer`
    override def getTemplate: Modifier = div(
      // loads Bootstrap and FontAwesome styles from CDN
      UdashBootstrap.loadBootstrapStyles(),
      UdashBootstrap.loadFontAwesome(),

      BootstrapStyles.container,
      fragment.header(model.subProp(_.athleteName), model.subProp(_.userId)),
      childViewContainer,
      fragment.footer
    )
  }

  class PageViewFactory(application: Application[RoutingState]) extends ViewFactory[RootState.type] {

    import scala.concurrent.ExecutionContext.Implicits.global

    override def create(): (View, Presenter[RootState.type]) = {
      val model = ModelProperty(PageModel(null, facade.UdashApp.currentUserId.orNull))
      for (userAPI <- facade.UdashApp.currentUserId.toOption.map(com.github.opengrabeso.mixtio.rest.RestAPIClient.api.userAPI)) {
        userAPI.name.foreach(name => model.subProp(_.athleteName).set(name))
      }

      val presenter = new PagePresenter(model, application)

      val view = new View(model, presenter)
      (view, presenter)
    }
  }

}