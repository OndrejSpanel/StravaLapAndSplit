package com.github.opengrabeso.mixtio
package cssrenderer

import io.udash.css._
import common.css._

import scalacss.internal.{Renderer, StringRenderer}
/** Renderer of styles based on UdashCSS. */
object CssRenderer {
  private val styles = Seq(
    GlobalStyles,
    AboutPageStyles
  )

  def renderToString: String = {
    implicit val renderer: Renderer[String] = StringRenderer.defaultPretty
    new CssStringRenderer(styles).render()
  }

  def main(args: Array[String]): Unit = {

    args match {
      case Array(path, renderPrettyString) =>
        val renderPretty = renderPrettyString.toBoolean
        implicit val renderer: Renderer[String] = if (renderPretty) StringRenderer.defaultPretty else StringRenderer.formatTiny
        renderPrettyString.toBoolean -> new CssFileRenderer(path, styles, createMain = true).render()
      case _ =>
        println(renderToString)
    }
  }
}