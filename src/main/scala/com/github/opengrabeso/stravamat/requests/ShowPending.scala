package com.github.opengrabeso.stravamat.requests

trait ShowPending {
  def pendingHTML = <div class="modal hide" id="pleaseWaitDialog" data-backdrop="static" data-keyboard="false">
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
    </div>
}
