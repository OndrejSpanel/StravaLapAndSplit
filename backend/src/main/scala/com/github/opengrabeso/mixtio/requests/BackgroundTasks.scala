package com.github.opengrabeso.mixtio
package requests

import com.google.appengine.api.taskqueue.{DeferredTask, QueueFactory, TaskHandle, TaskOptions}

object BackgroundTasks {
  def addTask(task: DeferredTask): TaskHandle = {
    val queue = QueueFactory.getDefaultQueue
    queue add TaskOptions.Builder.withPayload(task)

  }
}
