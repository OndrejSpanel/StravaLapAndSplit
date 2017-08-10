package com.github.opengrabeso.stravamat
package requests
package push

package object upload {

  case class Progress(session: String, total: Int, done: Int)

  def startProgress(userId: String, session: String, totalFiles: Int): Unit = {
    //println(s"Save progress $doneFiles/$totalFiles to $session")
    DStorage.store(Main.namespace.uploadProgress, "progress", userId, Progress(session, totalFiles, 0))
  }


  def reportProgress(userId: String, session: String, doneFiles: Int = 1): Unit = {
    //println(s"Save progress $doneFiles/$totalFiles to $session")
    DStorage.modify[upload.Progress](Main.namespace.uploadProgress, "progress", userId) { progress =>
      if (progress.session == session) {
        val r = progress.copy(done = progress.done + doneFiles)
        println(s"    $progress")
        r
      } else progress

    }
  }

  def loadProgress(userId: String) = {
    DStorage.load[upload.Progress](Main.namespace.uploadProgress, "progress", userId)
  }



}
