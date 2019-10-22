package com.github.opengrabeso.mixtio
package frontend
package services

import common.model.UserContext

import scala.concurrent.{ExecutionContext, Future}

class UserContextService(rpc: rest.RestAPI)(implicit ec: ExecutionContext) {
  private var userContext: Option[UserContext] = None

  def login(userId: String, authCode: String): UserContext = {
    val ctx = UserContext(userId, authCode)
    userContext = Some(ctx)
    ctx
  }
  def logout(): Future[UserContext] = {
    userContext.flatMap { ctx =>
      api.map(_.logout.map(_ => ctx))
    }.getOrElse(Future.failed(new UnsupportedOperationException))
  }

  def userName: Option[Future[String]] = api.map(_.name)
  def userId: Option[String] = userContext.map(_.userId)

  // TODO: double check authCode usage is safe here (it should be, we are frontend only here)
  def api: Option[rest.UserRestAPI] = userContext.map { ctx =>
    rpc.userAPI(ctx.userId, ctx.authCode)
  }
}
