package com.github.opengrabeso.stravamat
package requests

import scala.xml.NodeSeq

trait ShowPending extends HtmlPart {

  // https://github.com/raphaelfabeni/css-loader
  abstract override def headerPart(req: Request, auth: StravaAuthResult): NodeSeq = {
    super.headerPart(req, auth) ++
    <link rel="stylesheet" href="static/css-loader.css" type="text/css" media="screen" />
    <script>
      function showPending() {{
        $('#loader').addClass('is-active');
      }}
      function hidePending() {{
        $('#loader').removeClass('is-active');
      }}
    </script>
  }


  abstract override def bodyPart(req: Request, auth: StravaAuthResult): NodeSeq = {
    super.bodyPart(req, auth) ++
    <div id="loader" class="loader loader-default" data-text=""></div>
  }


}
