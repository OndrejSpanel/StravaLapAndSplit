package com.github.opengrabeso.mixtio
package rest

import scala.concurrent.{ExecutionContext, Future}

object RestAPIServer extends RestAPI {

  def syncResponse[T](t: =>T): Future[T] = {
    Future.successful(t)
  }
  // note: Not very efficient, there is no thread pool. Use only for heavy-weight tasks
  def asyncResponse[T](t: =>T): Future[T] = {
    val threadFactory = com.google.appengine.api.ThreadManager.currentRequestThreadFactory()

    implicit object EC extends ExecutionContext {
      def execute(runnable: Runnable) = {
        val t = threadFactory.newThread(runnable)
        t.start()
      }
      def reportFailure(cause: Throwable) = cause.printStackTrace()
    }
    Future {
      t
    }

  }

  def identity(in: String) = {
    syncResponse(in)
  }
}
