package com.github.opengrabeso.mixtio

import org.scalatest._

class SharedCode extends FunSuite {
  test("Use shared code") {
    val name = appName
    assert(name.nonEmpty)
    info(s"Shared function running OK on JS: $name")

  }
}
