package com.github.opengrabeso.mixtio

import io.udash.css._
import common.css._

import scalacss.internal.{Renderer, StringRenderer}
/** Renderer of styles based on UdashCSS. */
class CssRenderer(path: String, renderPretty: Boolean) {
  private val renderer: Renderer[String] =
    if (renderPretty) StringRenderer.defaultPretty
    else StringRenderer.formatTiny
  def render(): Unit = {
    new CssFileRenderer(
      path,
      Seq(
        // you have to put here all your CssBase classes
        GlobalStyles
      ),
      // it allows you to include all styles with the single
      createMain = true
    ).render()(renderer)
  }
}
object CssRenderer {
  // we want to make it runnable without
  // starting the whole application
  def main(args: Array[String]): Unit = {
    require(args.length == 2)
    new CssRenderer(
      args(0), java.lang.Boolean.parseBoolean(args(1))
    ).render()
  }
}