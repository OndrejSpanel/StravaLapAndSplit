package com.github.opengrabeso.mixtio
package frontend.views.about

import common.model._
import io.udash.HasModelPropertyCreator

// first parameter (h) is Mixtio staged activity
// second parameter (a) is corresponding Strava activity ID

case class ActivityRow(staged: ActivityHeader, strava: Option[ActivityId])

object ActivityRow extends HasModelPropertyCreator[ActivityRow]
