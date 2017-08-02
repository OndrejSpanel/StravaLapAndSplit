package com.github.opengrabeso.stravalas
package requests
package push
package upload

import spark.{Request, Response}
import org.joda.time.{DateTime => ZonedDateTime}
import DateTimeOps._

object PutCheck extends DefineRequest("/push-put-check") {

  override def html(request: Request, resp: Response) = {
    val session = request.session
    val userId = request.queryParams("user")

    <server>
      <since>

      </since>
    </server>
  }


}
