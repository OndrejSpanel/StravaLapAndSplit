package com.github.opengrabeso.stravamat
package requests

trait ShowPending extends HtmlPart {

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    <link rel="stylesheet" href="static/spinner.css" type="text/css" media="screen" />
    <script>
      function showPending() {{
      $('#mySpinner').addClass('spinner');
      }}
      function hidePending() {{
      $('#mySpinner').removeClass('spinner');
      }}
    </script>
  }

  def spinnerHere = {
    // TODO: https://stephanwagner.me/only-css-loading-spinner
    //<div id="mySpinner"></div>

    <div id="mySpinner">
      <span onclick="$('#mySpinner').addClass('spinner');" style="padding: 0 10px 0 0; cursor: pointer;">Add spinner!</span>
      <span style="padding: 0 0 0 10px; cursor: pointer;">Remove spinner!</span>
    </div>
  }


}
