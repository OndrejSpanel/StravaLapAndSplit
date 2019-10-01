package com.github.opengrabeso.mixtio
package requests

import common.model.FileId
import com.github.opengrabeso.mixtio.Main.namespace
import spark.{Request, Response}

import shared.Util._

object ProcessEdit extends ProcessFile("/edit-activities") with UploadResults {

  override def html(req: Request, resp: Response) = {
    startUploadSession(req.session())

    super.html(req, resp)
  }

  override def processAll(split: Seq[(Int, Main.ActivityEvents)], id: String)(req: Request, resp: Response) = {
    val session = req.session()
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val upload = split.map {
      case (_, act) =>
        val eventsWithBegEnd = (BegEvent(act.id.startTime, act.id.sportName) +: EndEvent(act.id.endTime) +: act.events).sortBy(_.stamp)
        act.copy(events = eventsWithBegEnd)
    }.reduce(_ merge _)

    // filename is not strong enough guarantee of uniqueness, timestamp should be (in single user namespace)
    val uniqueName = upload.id.id.filename + "_" + System.currentTimeMillis().toString
    Storage.store(namespace.edit, uniqueName, auth.userId, upload.header, upload)

    // report back the edited ID
    <activity>
      <id>
        {FileId.TempId(uniqueName)}
      </id>
    </activity>
  }

}
