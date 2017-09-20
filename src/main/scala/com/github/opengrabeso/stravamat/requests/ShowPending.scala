package com.github.opengrabeso.stravamat
package requests

trait ShowPending extends HtmlPart {

  abstract override def bodyPart(req: Request, auth: Main.StravaAuthResult) = {
    super.bodyPart(req, auth) ++
    <div class="modal-header">
      <h1>Please Wait</h1>
    </div>
    <div class="modal-body">
      <div id="ajax_loader">
        <p>Loading ...</p>
      </div>
    </div>
    <script>
      function showPending() {{
      $('# pleaseWaitDialog').modal();
      }}
      function hidePending() {{
      $('# pleaseWaitDialog').modal();
      }}
    </script>
  }
}
