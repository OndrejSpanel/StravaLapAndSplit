package com.github.opengrabeso.mixtio.common.css

import io.udash.css._

import scala.language.postfixOps

object AboutPageStyles extends CssBase {

  import dsl._

  val textCenter: CssStyle = style(
    textAlign.center
  )

  val infoIcon: CssStyle = style(
    fontSize(1 rem)
  )

  val container: CssStyle = style(
    margin.auto,
    marginTop(50 px),

    padding(25 px),
    borderColor.lightgray,
    borderRadius(10 px),
    borderStyle.solid,
    borderWidth(1 px)
  )

  private val minWide = 1000 px
  val wideMedia = style(
    media.not.all.minWidth(minWide)(
      display.none
    )
  )
  val narrowMedia = style(
    media.minWidth(minWide)(
      display.none
    )
  )
}
