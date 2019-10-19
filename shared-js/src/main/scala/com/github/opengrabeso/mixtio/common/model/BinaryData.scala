package com.github.opengrabeso.mixtio
package common.model

case class BinaryData(data: Array[Byte])

// it should be possible to create some more efficient encoding if necessary
// currently the Array serialized to JSON, producing signed decimal representation of bytes delimited by commas

object BinaryData extends rest.EnhancedRestDataCompanion[BinaryData]
