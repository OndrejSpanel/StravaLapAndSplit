package com.github.opengrabeso.stravalas
package requests
import spark.{Request, Response}

object Cleanup extends DefineRequest("/cleanup") {
  def html(request: Request, resp: Response) = {
    val periodic = request.queryParams("periodic")
    if (periodic != null) {
      val cleaned = Storage.cleanup()

      <cleaned><files>{cleaned.toString}</files></cleaned>
    } else {
      resp.status(400) // Bad Request
      <cleaned><error>Syntax error</error></cleaned>
    }
  }
}
