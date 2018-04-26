package com.github.opengrabeso.stravimat
package requests

trait ActivitiesTable extends HtmlPart {
  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    <style>
      .activities tr:nth-child(even) {{background-color: #e8e8e8}}
      .activities tr:hover {{background-color: #f0f0e0}}
    </style>
  }

}
