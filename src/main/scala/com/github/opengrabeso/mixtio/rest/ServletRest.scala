package com.github.opengrabeso.mixtio
package rest

import javax.servlet._
import javax.servlet.http.HttpServletResponse

class ServletRest extends GenericServlet {
  def service(req: ServletRequest, res: ServletResponse) = {
    val httpResponse = res.asInstanceOf[HttpServletResponse]
    httpResponse.setStatus(204)
  }
}
