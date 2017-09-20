package com.github.opengrabeso.stravamat
package requests

trait ShowPending extends HtmlPart {

  abstract override def bodyPart(req: Request, auth: Main.StravaAuthResult) = {
    super.bodyPart(req, auth) ++
    <div class="modal hide" id="pleaseWaitDialog" data-backdrop="static" data-keyboard="false" style="display:none">
      <div class="modal-header">
        <h1>Please Wait</h1>
      </div>
      <div class="modal-body">
        <div id="ajax_loader">
          <p>Loading ...</p>
        </div>
      </div>
    </div>
    <script>
      function showPending() {{
      $('#pleaseWaitDialog').modal();
      }}
      function hidePending() {{
      $('#pleaseWaitDialog').modal();
      }}
    </script>
  }

  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    <script src="static/jquery.modal.min.js" type="text/javascript" charset="utf-8"></script>
    <link rel="stylesheet" href="static/jquery.modal.min.css" type="text/css" media="screen" />
  }



}
