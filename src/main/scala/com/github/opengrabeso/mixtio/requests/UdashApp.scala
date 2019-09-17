package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object UdashApp  extends DefineRequest("app") {
  def html(request: Request, resp: Response) = {
    <html>
      <head>
        <title>{appName}</title>{headPrefix}
        <script src="js/script"></script>
        <script src="js/dependencies"></script>
        <script>appMain()</script>
      </head>
      <body>
        <div id="application"></div>
      </body>
    </html>
  }

}
