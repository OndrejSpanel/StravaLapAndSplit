package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object Version  extends DefineRequest("version") {
  def html(request: Request, resp: Response) = {
    <html>
      <head>
        <title>{shared.appName}</title>{headPrefix}
        <script src="js/script"></script>
        <script src="js/dependencies"></script>
      </head>
      <body>
        Script loaded. TODO: show some output
        <p>
          Name:
          <script>
            document.write({xml.Unparsed("MainJS.appName")})
          </script>
        </p>
      </body>
    </html>
  }

}
