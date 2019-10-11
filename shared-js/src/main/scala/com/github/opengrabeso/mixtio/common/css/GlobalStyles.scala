package com.github.opengrabeso.mixtio.common.css

import io.udash.css._

import scala.language.postfixOps

object GlobalStyles extends CssBase {
  import dsl._

  val floatRight: CssStyle = style(
    float.right
  )

  val messagesWindow: CssStyle = style(
    height :=! "calc(100vh - 220px)",
    overflowY.auto
  )

  val msgDate: CssStyle = style(
    marginLeft(5 px),
    fontSize(0.7 em),
    color.gray
  )

  val msgContainer: CssStyle = style(
    unsafeChild(s".${msgDate.className}")(
      display.none
    ),

    &.hover(
      unsafeChild(s".${msgDate.className}")(
        display.initial
      )
    )
  )

  val header: CssStyle = style(
    backgroundColor(c"#fca"),
    overflow.auto,
    flexGrow(0).important, // otherwise .container > div overrides
    flexDirection.column
  )

  val footer: CssStyle = style(
    backgroundColor(c"#fca"),
    flexGrow(0).important, // otherwise .container > div overrides
    overflow.auto
  )

  val stravaImg: CssStyle = style(
    maxHeight.apply(46 px)
  )

  val footerText: CssStyle = style(
    color.white
  )
  val footerLink: CssStyle = style(
    color.inherit
  )

  style(
    unsafeRoot("body")(
      display.flex,
      flexDirection.column,
      padding.`0`,
      margin.`0`,
      height(100 %%)
    ),
    unsafeRoot("html")(
      padding.`0`,
      margin.`0`,
      height(100 %%)
    ),
    unsafeRoot("#application")(
      display.flex,
      flexDirection.column,
      flexGrow(1)
    ),

    unsafeRoot(".container")(
      maxWidth(100 vw).important, // remove default Bootstrap width limitations
      display.flex,
      flexDirection.column,
      flexGrow(1)
    ),
    unsafeRoot(".container > div")(
      display.flex,
      flexDirection.column,
      flexGrow(1)
    )

  )
}
