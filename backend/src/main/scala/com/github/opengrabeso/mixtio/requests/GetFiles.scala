package com.github.opengrabeso.mixtio
package requests

trait GetFiles extends HtmlPart {
  abstract override def headerPart(req: Request, auth: StravaAuthResult) = {
    super.headerPart(req, auth) ++
    <script src="static/ajaxUtils.js"></script>
  }

  abstract override def bodyPart(req: Request, auth: StravaAuthResult) = {
    super.bodyPart(req, auth) ++
    <h2>Staging</h2>

      <form action="upload" method="post" enctype="multipart/form-data">
        <input type="hidden" id="timezone" name="timezone" value=""></input>
        <p>Select files to upload

          <div id="drop-container" style="border:1px solid black;height:100px;">
            Drop Here
          </div>

          <input type="file" id="fileInput" name="activities" multiple="multiple" accept=".fit,.gpx,.tcx,.sml,.xml"/>
        </p>
        <input type="submit" value="Upload"/>
      </form>
      <script>{xml.Unparsed(
        //language=JavaScript
        """

      // dragover and dragenter events need to have 'preventDefault' called
      // in order for the 'drop' event to register.
      // See: https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Drag_operations#droptargets
      var dropContainer = document.getElementById("drop-container");

      dropContainer.addEventListener("dragover", function (evt){
        evt.preventDefault();
      });
      dropContainer.addEventListener("dragenter", function (evt){
        evt.preventDefault();
      });
      dropContainer.addEventListener("drop", function (evt){
        // pretty simple -- but not for IE :(
        fileInput.files = evt.dataTransfer.files;
        evt.preventDefault();
      });


      function changeActivity(event, value, id) {
        console.log("changeActivity " + event + ",");
      }

      document.getElementById("timezone").value = Intl.DateTimeFormatter().resolvedOptions().timeZone

      """
      )}
      </script>
  }
}
object GetFiles extends DefineRequest("/getFiles") with HtmlByParts with GetFiles with Headers