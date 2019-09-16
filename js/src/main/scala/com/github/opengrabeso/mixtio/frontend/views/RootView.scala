package com.github.opengrabeso.mixtio
package frontend
package views

import shared.css._
import routing._

import io.udash._
import io.udash.bootstrap._
import io.udash.css._

class RootViewFactory extends StaticViewFactory[RootState.type](
  () => new RootView
)

class RootView extends ContainerView with CssView {
  import scalatags.JsDom.all._

  // ContainerView contains default implementation of child view rendering
  // It puts child view into `childViewContainer`
  override def getTemplate: Modifier = div(
    // loads Bootstrap and FontAwesome styles from CDN
    UdashBootstrap.loadBootstrapStyles(),
    UdashBootstrap.loadFontAwesome(),

    BootstrapStyles.container,
    div(GlobalStyles.floatRight),
    h1("Udash Mixtio"),
    childViewContainer
  )
}