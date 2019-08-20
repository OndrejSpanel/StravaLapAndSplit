package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object JSTest extends DefineRequest("test") {

  def html(request: Request, resp: Response) = {
    // local server uses fastopt, real server uses
    val jsPath = if (Main.devMode) "/js-fastOpt.js" else "/js-opt.js"
    val res = getClass.getResourceAsStream(jsPath)
    val source = scala.io.Source.fromInputStream(res)
    val jsContent = source.getLines.mkString
    source.close()

    <html>
      <head>
        <title>{shared.appName}</title>{headPrefix}
        <script>{xml.Unparsed(jsContent)}</script>
      </head>
      <body>
        This is just a simple thing that I made on my own.
      </body>
    </html>
  }
}
