package com.github.opengrabeso.stravalas
package requests
package push
package upload
import spark.{Request, Response}

object ListPending extends DefineRequest("/push-list-pending") {
  def html(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sessionId = uniqueSessionId(session)
    //val stravaActivities = session.attribute[Seq[Main.ActivityId]]("stravaActivities")

    //val stored = Storage.enumerate(Main.namespace.stage, auth.userId)


    val progress = upload.loadProgress(auth.userId)
    // to do the matching we would have to load the stored activities

    def unknownProgress = {
      <progress>
        <unknown></unknown>
      </progress>
    }

    progress.fold {
      unknownProgress
    } { p =>
      //println(s"Load progress ${p.done}/${p.total} to ${p.session}")
      // now do the matching and list those not matching
      if (sessionId == p.session) {
        <progress>
          <total>{p.total}</total>
          <done>{p.done}</done>
        </progress>
      } else {
        unknownProgress
      }
    }
  }
}
