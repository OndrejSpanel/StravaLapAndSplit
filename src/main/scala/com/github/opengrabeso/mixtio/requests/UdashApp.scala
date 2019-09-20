package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object UdashApp  extends DefineRequest("app") {
  def html(request: Request, resp: Response) = withAuth(request, resp) { auth =>
    <html>
      <head>
        <title>{appName}</title>{headPrefix}
        <script src="js/script"></script> {/* scala.js compilation result */}
        <script src="js/dependencies"></script>{/* scala.js dependencies */}
        <link href="styles/main.css" rel="stylesheet" /> {/* Udash generated stylesheet*/ }
        <script>
          var currentUserId = '{auth.userId}';
          appMain()
        </script>
      </head>
      <body>
        <div id="application"></div>
      </body>
    </html>
  }

}
