package com.github.opengrabeso.stravimat
package requests
package push

import spark.{Request, Response}

object PushDo extends DefineRequest("/push-do") with ChangeSettings {
  override def showSuuntoUploadInstructions = false // no need to show this, user already launched it
  def html(req: Request, resp: Response) = {

    withAuth(req, resp) { auth =>
      val settings = Settings(auth.userId)
      // display push progress, once done, let user to process it
      <html>
        <head>
          {headPrefix}<title>{shared.appName} - uploading Suunto files</title>
          <script src="static/ajaxUtils.js"></script>
          <script src="static/jquery-3.2.1.min.js"></script>
        </head>
        <body>
          {bodyHeader(auth)}<h2>Receiving Suunto files ...</h2>
          <p>
            <span id="done">--</span>
            of
            <span id="total">--</span>
          </p>{suuntoSettings(settings)}<script>
          {xml.Unparsed(
            //language=JavaScript
            """
          function update() {
            $.ajax({
              url: "push-list-pending",
              dataType: "xml",
              cache: false
            }).done( function(response) {
              var totalElem = $(response).find("total");
              var doneElem = $(response).find("done");
              if (/*totalElem && doneElem &&*/ totalElem.length > 0 && doneElem.length > 0) {
                var totalFiles = parseInt(totalElem.first().text());
                var doneFiles = parseInt(doneElem.first().text());
                $("#total").html(totalFiles);
                $("#done").html(doneFiles);
                if (doneFiles < totalFiles) setTimeout(update, 1000);
                else $("#uploaded").show();
              } else setTimeout(update, 1000);
            });
          }
          update();
          """)}
        </script>
          <div id="uploaded" style="display: none;">
            <h2>Done</h2>{// TODO: integrate selectActivity functionality here
            }<a href="selectActivity">Select activity</a>
          </div>{bodyFooter}
        </body>
      </html>
    }

  }
}