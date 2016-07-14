package com.github.opengrabeso.stravalas

import com.garmin.fit.{BufferEncoder, MesgDefinitionListener, MesgListener}

object FitExport {
  type Encoder = MesgListener with MesgDefinitionListener

  private def createEncoder: Encoder = {
    new BufferEncoder
  }

  
}
