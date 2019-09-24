package com.github.opengrabeso.mixtio
package requests

import org.apache.commons.io.IOUtils
import spark.{Request, Response}

object ScalaJSScript extends DefineRequest("frontend/*") {

  def html(request: Request, resp: Response) = {
    val scriptName = request.splat().head
    val moduleName = "frontend"
    val jsPath = scriptName match {
      case "script" =>
        Some(if (Main.devMode) s"/$moduleName-fastopt.js" else s"/$moduleName-opt.js")
      case "dependencies" =>
        Some(if (Main.devMode) s"/$moduleName-jsdeps.js" else s"/$moduleName-jsdeps.min.js")
      case "main.css" =>
        Some("/main.css")
      case _ =>
        None
    }
    jsPath.fold {
      resp.status(404)
    } { jsPath =>
      val res = getClass.getResourceAsStream(jsPath)

      resp.status(200)
      resp.`type`("application/json")

      val out = resp.raw.getOutputStream
      IOUtils.copy(res, out)
      IOUtils.write("\n", out) // prevent empty file by always adding an empty line, empty file not handled well by Spark framework
    }

    Nil
  }
}
