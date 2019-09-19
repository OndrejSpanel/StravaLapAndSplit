package com.github.opengrabeso.mixtio.common.model

import io.udash.rest.RestDataCompanion

case class UserContext(name: String, userId: String)
object UserContext extends RestDataCompanion[UserContext]
