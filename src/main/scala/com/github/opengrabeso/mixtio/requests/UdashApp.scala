package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object UdashApp  extends DefineRequest("app") {
  def html(request: Request, resp: Response) = withAuth(request, resp) { auth =>
    <html>
      <head>
        <title>{appName}</title>{headPrefix}
        <script src="js/script"></script>
        <script src="js/dependencies"></script>
        <script>
          currentUserId = '{auth.userId}';
          appMain()
        </script>
      </head>
      <body>
        <div id="application"></div>
      </body>
    </html>
  }

}
