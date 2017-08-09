package com.github.opengrabeso.stravamat.requests.push

import com.github.opengrabeso.stravamat.{Main, Storage}

package object upload {

  case class Progress(session: String, total: Int, done: Int)

  def startProgress(userId: String, session: String, totalFiles: Int): Unit = {
    //println(s"Save progress $doneFiles/$totalFiles to $session")
    synchronized {
      Storage.store(Main.namespace.uploadProgress, "progress", userId, Progress(session, totalFiles, 0))
    }
  }


  def reportProgress(userId: String, session: String, doneFiles: Int = 1): Unit = {
    //println(s"Save progress $doneFiles/$totalFiles to $session")
    synchronized {
      for (progress <- Storage.load[upload.Progress](Main.namespace.uploadProgress, "progress", userId)) {
        if (progress.session == session) {
          Storage.store(Main.namespace.uploadProgress, "progress", userId, progress.copy(done = progress.done + doneFiles))
        }
      }
    }
  }

  def loadProgress(userId: String) = {
    synchronized {
      Storage.load[upload.Progress](Main.namespace.uploadProgress, "progress", userId)
    }
  }



}
