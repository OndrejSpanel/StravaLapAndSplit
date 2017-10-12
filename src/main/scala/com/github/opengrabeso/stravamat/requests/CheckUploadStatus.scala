package com.github.opengrabeso.stravamat
package requests

import spark.{Request, Response}

object CheckUploadStatus extends DefineRequest.Post("/check-upload-status")  {
  def html(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sessionId = session.attribute[String]("sid")

    if (auth == null || sessionId == null) {
      <error>Unknown upload session</error>
    } else {

      val uploadNamespace = Main.namespace.upload(sessionId)
      val uploadResultNamespace = Main.namespace.uploadResult(sessionId)

      val requests = Storage.enumerate(uploadNamespace, auth.userId)
      val resultsFiles = Storage.enumerate(uploadResultNamespace, auth.userId)

      val results = for {
        (_, resultFilename) <- resultsFiles
        status <- Storage.load[UploadStatus](Storage.FullName(uploadResultNamespace, resultFilename, auth.userId))
      } yield {
        <result>
          {
            val ret = status.xml
            // once reported, delete it
            if (ret.nonEmpty) Storage.delete(Storage.FullName(uploadResultNamespace, resultFilename, auth.userId))
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
}
