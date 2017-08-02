package com.github.opengrabeso.stravalas
package requests
package push

import spark.{Request, Response}

object PushLogout extends DefineRequest("/push-logout") {
  override def urlPrefix = "push-"
  def html(request: Request, resp: Response) = {
    resp.cookie("authCode", "", 0)

    <html>
      <head>
        {headPrefix}<title>Stravamat - loggin out</title>
      </head>
      <body>
        Logging out ...
        If you want to upload more files, launch the Start application again.
        {
          //TODO: use JNLP to offer downloading the file here
        }
      </body>
    </html>
  }
}
