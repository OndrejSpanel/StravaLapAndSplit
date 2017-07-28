package com.github.opengrabeso.stravalas
package requests

import com.github.opengrabeso.stravalas.Main.namespace
import spark.{Request, Response}

object CheckUploadStatus extends DefineRequest.Post("/check-upload-status")  {
  def html(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val resultsFiles = Storage.enumerate(namespace.uploadResult, auth.userId)

    val results = {for {
      resultFilename <- resultsFiles
      status <- Storage.load[UploadStatus](namespace.uploadResult, resultFilename, auth.userId)
    } yield {
      <result>
        {
          val ret = status.xml
          // once reported, delete it
          if (ret.nonEmpty) Storage.delete(namespace.uploadResult, resultFilename, auth.userId)
          ret
        }
      </result>
    }}
    <status>
      {results}
    </status>

  }
}
