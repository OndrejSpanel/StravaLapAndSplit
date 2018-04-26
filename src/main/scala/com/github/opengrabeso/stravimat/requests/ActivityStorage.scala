package com.github.opengrabeso.stravimat
package requests

import Main._

trait ActivityStorage {

  def storeActivity(stage: String, act: ActivityEvents, userId: String) = {
    Storage.store(stage, act.id.id.filename, userId, act.header, act, Seq("digest" -> act.id.digest), Seq("startTime" -> act.id.startTime.toString))
  }


  def loadActivity(stage: String, actId: FileId, userId: String) = {
    val fullName = Storage.getFullName(Main.namespace.stage, actId.filename, userId)

    Storage.load[ActivityHeader, ActivityEvents](fullName).map(_._2)
  }



}
