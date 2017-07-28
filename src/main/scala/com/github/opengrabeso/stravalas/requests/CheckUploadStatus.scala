package com.github.opengrabeso.stravalas
package requests

import com.github.opengrabeso.stravalas.Main.namespace
import spark.{Request, Response}

object CheckUploadStatus extends DefineRequest.Post("/check-upload-status")  {
  def html(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sessionId = req.queryParams("sid").toLong

    val uploadNamespace = Main.namespace.upload(sessionId)
    val uploadResultNamespace = Main.namespace.uploadResult(sessionId)

    val requests = Storage.enumerate(uploadNamespace, auth.userId)
    val resultsFiles = Storage.enumerate(uploadResultNamespace, auth.userId)

    val results = for {
      resultFilename <- resultsFiles
      status <- Storage.load[UploadStatus](uploadResultNamespace, resultFilename, auth.userId)
    } yield {
      <result>
        {
          val ret = status.xml
          // once reported, delete it
          if (ret.nonEmpty) Storage.delete(uploadResultNamespace, resultFilename, auth.userId)
          ret
        }
      </result>
    }
    val complete = <complete></complete>

    val ret = if (resultsFiles.nonEmpty || requests.nonEmpty) results else complete
    <status>
      {ret}
    </status>

  }
}
