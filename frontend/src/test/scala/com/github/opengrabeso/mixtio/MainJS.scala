package com.github.opengrabeso.mixtio

import org.scalatest._

class MainJS extends FunSuite {
  test("Dummy test") {
    info("Test running OK")
  }
  test("Use shared code") {
    val name = appName
    assert(name.nonEmpty)
    info(s"Shared function running OK on JVM: $name")
  }
}
