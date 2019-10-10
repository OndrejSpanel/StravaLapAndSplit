package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object UdashApp  extends DefineRequest("app") {
  /*
  sessionId is a time when the initial session page was rendered on the server. A new page will constitute a new session.
  */
  def html(request: Request, resp: Response) = withAuth(request, resp) { auth =>
    <html>
      <head>
        <title>{appName}</title>{headPrefix}
        <script src="frontend/script"></script> {/* scala.js compilation result */}
        <script src="frontend/dependencies"></script>{/* scala.js dependencies */}
        <link href="frontend/main.css" rel="stylesheet" /> {/* Udash generated stylesheet*/ }
        <script src='https://api.mapbox.com/mapbox-gl-js/v1.0.0/mapbox-gl.js'></script>
        <link href='https://api.mapbox.com/mapbox-gl-js/v1.0.0/mapbox-gl.css' rel='stylesheet' />
        <script>
          var currentUserId = '{auth.userId}';
          var sessionId = `{System.currentTimeMillis().toString}`; // time when the session was created on the server
          appMain()
        </script>
      </head>
      <body>
        <div id="application"></div>
      </body>
    </html>
  }

}
