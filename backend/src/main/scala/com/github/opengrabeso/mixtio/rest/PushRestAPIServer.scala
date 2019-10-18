package com.github.opengrabeso.mixtio
package rest

class PushRestAPIServer(parent: UserRestAPIServer) extends PushRestAPI with RestAPIUtils {

  def offerFiles(files: Seq[(String, String)]) = syncResponse {
    ???
  }

  // upload a single file
  def uploadFile(id: String, content: Array[Byte]) = syncResponse {
    ???
  }

}
