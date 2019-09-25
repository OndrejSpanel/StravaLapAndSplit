package com.github.opengrabeso.mixtio.cssrenderer

import org.scalatest.{FunSuite, Inspectors, Matchers}

class CssRendererTest extends FunSuite with Matchers with Inspectors {
  val css = CssRenderer.renderToString
  test("CSS should be reasonable") {
    val mustContain = Seq(
      "{", "}"
    )
    forAll(mustContain) { s =>
      css should contain(s)
    }
  }
}
