package com.github.opengrabeso.mixtio
package frontend.views.select

import common.model._
import io.udash.HasModelPropertyCreator

// first parameter (h) is Mixtio staged activity
// second parameter (a) is corresponding Strava activity ID

case class ActivityRow(staged: ActivityHeader, strava: Option[ActivityId], selected: Boolean, uploading: Boolean = false, uploadState: String = "")

object ActivityRow extends HasModelPropertyCreator[ActivityRow]
