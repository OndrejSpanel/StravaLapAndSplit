package com.github.opengrabeso.mixtio

import javax.servlet.{Servlet, ServletConfig, ServletRequest, ServletResponse}

class ServletApp extends Servlet {
  override def init(config: ServletConfig) = ???
  override def getServletConfig = ???
  override def service(req: ServletRequest, res: ServletResponse) = ???
  override def getServletInfo = ???
  override def destroy() = ???
}
