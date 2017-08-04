package com.github.opengrabeso.stravalas.requests.push

import com.github.opengrabeso.stravalas.{Main, Storage}

package object upload {
  case class Progress(session: String, total: Int, done: Int)

  def saveProgress(userId: String, session: String, totalFiles: Int, doneFiles: Int): Unit = {
    Storage.store(Main.namespace.uploadProgress, "progress", userId, Progress(session, totalFiles, doneFiles))
  }

}
