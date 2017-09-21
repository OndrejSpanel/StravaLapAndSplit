package com.github.opengrabeso.stravamat
package requests

trait ShowPending extends HtmlPart {

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
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

  def spinnerHere = {
    // TODO: https://stephanwagner.me/only-css-loading-spinner

    <div id="loader" class="loader loader-default"></div>
  }


}
