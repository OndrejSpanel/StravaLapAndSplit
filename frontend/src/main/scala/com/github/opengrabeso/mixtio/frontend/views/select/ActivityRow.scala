package com.github.opengrabeso.mixtio
package frontend.views.select

import common.model._
import io.udash.HasModelPropertyCreator

// first parameter (h) is Mixtio staged activity
// second parameter (a) is corresponding Strava activity ID

/**
  * uploading = true means upload is in progress or has completed (with error)
  * When uploadState is non-empty it means some error was encountered duting upload
  * */
case class ActivityRow(staged: ActivityHeader, strava: Option[ActivityId], selected: Boolean, uploading: Boolean = false, uploadState: String = "")

object ActivityRow extends HasModelPropertyCreator[ActivityRow]
