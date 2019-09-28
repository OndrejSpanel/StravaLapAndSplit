package com.github.opengrabeso.mixtio
package frontend
package services

import common.model.UserContext

import scala.concurrent.{ExecutionContext, Future}

class UserContextService(rpc: rest.RestAPI)(implicit ec: ExecutionContext) {
  private var userContext: Option[UserContext] = None

  def login(userId: String): Future[UserContext] = {
    val name = rpc.userAPI(userId).name
    name.map { s =>
      val ctx = UserContext(s, userId)
      userContext = Some(ctx)
      ctx
    }
  }
  def logout(): Future[UserContext] = {
    userContext.flatMap { ctx =>
      api.map(_.logout.map(_ => ctx))
    }.getOrElse(Future.failed(new UnsupportedOperationException))
  }

  def userName: Option[String] = userContext.map(_.name)
  def userId: Option[String] = userContext.map(_.userId)

  def api: Option[rest.UserRestAPI] = userContext.map(ctx => rpc.userAPI(ctx.userId))
}
