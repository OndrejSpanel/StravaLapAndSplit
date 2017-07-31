package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object GetSuunto extends DefineRequest("/getSuunto") with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")
    val sinceString = Option(request.queryParams("since"))

    val sincePar = sinceString.fold("")(s => s"?since=$s")

    val uploaderUri = "http://localhost:8088" // uploader should be running as a local web server
    val enumPath = "enum" // must be the same as in StravamatUploader // TODO: share sources

    // when returning, check referer to return to https if appropriate

    val referer = request.headers("referer")

    // display a page, the page will ask the local uploading server to send files
    <html>
      <head>
        {headPrefix}<title>Stravamat - upload Suunto files</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
      </head>
      <body>
        {bodyHeader(auth)}<h2>Getting files from Suunto ...</h2>

        <a href={referer}>Go back</a>
        <div id="myDiv"></div>
        <div id="status"></div>
        {bodyFooter}
        <script src="static/ajaxUtils.js"></script>
        <script>{xml.Unparsed(
          //language=JavaScript
          s"""
          var uploaderUri = "$uploaderUri";
          var filesToLoad = [];

          function dateFromFilename(name) {
             var gpsPat = /.*.*-(\\d*)-(\\d*)-(\\d*)T(\\d*)_(\\d*)_(\\d*)-.*/;
             var questPat = /.*\\/Quest_\\d*_(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)\\..*/;
             var gpsMatch = gpsPat.exec(name);
             var questMatch = gpsPat.exec(name);
             if (gpsMatch) {
               return " " + new Date(gpsMatch[1], gpsMatch[2] - 1, gpsMatch[3]).toLocaleDateString()
             } else if (questMatch) {
               return " " + new Date(questMatch[1], questMatch[2] - 1, questMatch[3]).toLocaleDateString()
             }
             return "";
          }

          function loadFile(file) {
            ajaxAsync(uploaderUri + "/digest?path=" + file, "", function(response) {
              var digest = response.documentElement.getElementsByTagName("value")[0].textContent;
              console.log("Digest " + file + "=" + digest);
              // check if digest is matching the server value
              ajaxAsync("putDigest?path=" + file, digest, function (digestResponse, digestCode) {
                // returns 200 (not present) or 204 (already present)
                if (digestCode === 200) {
                  ajaxAsyncRaw(uploaderUri + "/get?path=" + file, "", function (fileResponse) {
                    // fileResponse is array of bytes
                    displayProgress("Loading", file, fileResponse.byteLength);
                    console.log("Loaded bytes of "+ file + " : " + fileResponse.byteLength);
                    ajaxAsync("put?path=" + file, fileResponse, function (putResponse, putCode) {
                      console.log("Put file " + file + " code " + putCode);
                      loadNextFile();
                    });
                  });
                } else {
                  console.log("Digest " + file + " matching");
                  displayProgress("Skipping", file);
                  loadNextFile();
                }
              });
            });

          }

          /**
          @param {number?} fileSize
          @return string
          */
          function displayFileSize(fileSize) {
            if (fileSize) {
              if (fileSize > 1024*1024) {
                return ((fileSize + 512 * 1024) / (1024 * 1024)).toFixed(0) + " MB";
              } else {
                return ((fileSize + 512) / 1024).toFixed(0) + " KB";
              }
            } else return "";
          }

          /**
          @param {string} fileName
          @param {string} operation
          @param {number?} fileSize
          */
          function displayProgress(operation, fileName, fileSize) {
            document.getElementById("myDiv").innerHTML = fileName ?
              "<h3>" + operation + dateFromFilename(fileName) + " file '"  + fileName + "' " + displayFileSize(fileSize) + "</h3>" :
              "";
          }

          /**
           @param {string} status
           */
          function displayStatus(status) {
            document.getElementById("status").innerHTML = "<h4>" + status + "</h4>";

          }


          function loadNextFile() {
            var next = filesToLoad.shift();
            if (next) {
              console.log("loadNextFile: Left " + filesToLoad.length);
              displayStatus("Synchronizing " + (filesToLoad.length + 1) + " files");
              loadFile(next);
            } else {
              displayStatus("Synchronization completed");
              displayProgress(undefined);
            }
          }
          /**
           @param {Element} files
           */
          function loadAllFiles(files) {
            var items = files.getElementsByTagName("file");
            for (var i = 0; i < items.length; i++) {
              var file = items.item(i).textContent;
              filesToLoad.push(file);
            }
            loadNextFile();

          }

          function enumerate() {

            ajaxAsync(uploaderUri + "/$enumPath$sincePar", "", function sucess(response){
              document.getElementById("myDiv").innerHTML = "<h3>Loading files</h3>";
              loadAllFiles(response.documentElement)
            }, function failure() {
              document.getElementById("myDiv").innerHTML = "<h3>Uploader not responding</h3>";
            });
          }

          window.onload = enumerate();
          """
        )}
        </script>
      </body>
    </html>
  }
}
