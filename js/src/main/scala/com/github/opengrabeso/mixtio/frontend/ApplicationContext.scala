package com.github.opengrabeso.mixtio
package frontend

import routing._
import common.model._
import io.udash._

object ApplicationContext {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val routingRegistry = new RoutingRegistryDef
  private val viewFactoryRegistry = new StatesToViewFactoryDef

  val application = new Application[RoutingState](routingRegistry, viewFactoryRegistry)
  val userContextService = new services.UserContextService(com.github.opengrabeso.mixtio.rest.RestAPIClient.api)

  application.onRoutingFailure {
    case _: SharedExceptions.UnauthorizedException =>
      // automatic redirection to AboutPage
      println("A routing failure: UnauthorizedException")
      application.goTo(AboutPageState)
  }

  /*
  val notificationsCenter: NotificationsCenter = new NotificationsCenter

  // creates RPC connection to the server
  val serverRpc: MainServerRPC = DefaultServerRPC[MainClientRPC, MainServerRPC](
    new RPCService(notificationsCenter), exceptionsRegistry = new SharedExceptions
  )

  val restRpc: AdditionalRpc = {
    implicit val sttpBackend: SttpBackend[Future, Nothing] = SttpRestClient.defaultBackend()
    val (scheme, defaultPort) =
      if (dom.window.location.protocol == "https:") ("https", 443) else ("http", 80)
    val port = Try(dom.window.location.port.toInt).getOrElse(defaultPort)
    SttpRestClient[AdditionalRpc](s"$scheme://${dom.window.location.hostname}:$port/rest")
  }

  restRpc.ping("test").onComplete(println)

  val translationsService: TranslationsService = new TranslationsService(serverRpc.translations())
  val userService: UserContextService = new UserContextService(serverRpc)
  */
}