package com.github.opengrabeso.mixtio
package requests

import java.io.OutputStreamWriter

import org.apache.commons.io.IOUtils
import spark.{Request, Response}

object ScalaJSScript extends DefineRequest("js/*") {

  def html(request: Request, resp: Response) = {
    val scriptName = request.splat().head
    val jsPath = scriptName match {
      case "script" =>
        if (Main.devMode) "/js-fastopt.js" else "/js-opt.js"
      case "dependencies" =>
        if (Main.devMode) "/js-jsdeps.js" else "/js-jsdeps.min.js"
    }
    val res = getClass.getResourceAsStream(jsPath)

    resp.status(200)
    resp.`type`("application/json")

    val out = resp.raw.getOutputStream
    IOUtils.copy(res, out)
    IOUtils.write("\n", out) // prevent empty file by always adding an empty line, empty file not handled well by Spark framework

    Nil
  }
}
