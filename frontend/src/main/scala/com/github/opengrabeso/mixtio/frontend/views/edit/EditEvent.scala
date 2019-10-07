package com.github.opengrabeso.mixtio
package frontend
package views.edit

import io.udash.HasModelPropertyCreator

case class EditEvent(action: String, time: Int, km: Double)

// TODO: we will probably need a Rest companion as well
object EditEvent extends HasModelPropertyCreator[EditEvent]
