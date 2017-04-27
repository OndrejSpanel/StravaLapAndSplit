package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object GetSuunto extends DefineRequest("/getSuunto", method = Method.Get) with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val uploaderUri = "http://localhost:8088" // uploader should be running as a local web server
    val enumPath = "enum" // must be the same as in StravamatUploader // TODO: share sources

    // when returning, check referer to return to https if appropriate

    val referer = request.headers("referer")

    // display a page, the page will ask the local uploading server to send files
    <html>
      <head>
        {headPrefix}<title>Stravamat - select activity</title>
        <style>
          tr.activities:nth-child(even) {{background-color: #f2f2f2}}
          tr.activities:hover {{background-color: #f0f0e0}}
        </style>
      </head>
      <body>
        {bodyHeader(auth)}<h2>Getting files from Suunto ...</h2>

        <a href={referer}>Go back</a>
        <p>
        <div id="myDiv"></div>
        </p>
        {bodyFooter}
        <script>{xml.Unparsed(
          //language=JavaScript
          s"""
          var uploaderUri = "$uploaderUri";
          /**
           * @returns {XMLHttpRequest}
           */
          function /** XMLHttpRequest */ ajax() {
            var xmlhttp;
            if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari
              xmlhttp = new XMLHttpRequest();
            } else { // code  for IE6, IE5
              xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
            }
            return xmlhttp;
          }

          /**
          * @param {XMLHttpRequest} xmlhttp
          * @param {string} request
          * @param {string} [data]
          * @param {boolean} async
          */
          function ajaxPost(xmlhttp, request, data, async) {
            xmlhttp.open("POST", request, async); // POST to prevent caching
            xmlhttp.setRequestHeader("Content-type", "text/plain");
            xmlhttp.send(data ? data: "");
          }

          function ajaxAsync(uri, data, callback, failure) {
            var xmlhttp = ajax();
            // the callback function to be callled when AJAX request comes back
            xmlhttp.onreadystatechange = function () {
              if (xmlhttp.readyState === 4) {
                if (xmlhttp.status >= 200 && xmlhttp.status < 300) {
                  callback(xmlhttp.responseXML);
                } else if (failure) {
                  failure(xmlhttp.responseXML);
                }
              }
            };
            ajaxPost(xmlhttp, uri, data, true); // POST to prevent caching
          }

          function loadFile(file) {
            ajaxAsync(uploaderUri + "/digest?path=" + file, "", function(response) {
              var digest = response.documentElement.getElementsByTagName("value")[0].textContent;
              document.getElementById("myDiv").innerHTML = "<h3>Loading file '" + file + "' </h3>";
              console.log("Digest " + file + "=" + digest);
              // check if digest is matching the server value
              ajaxAsync("putDigest?path=" + file, digest, function () {
                console.log("Digest sent")
              });
            });

          }

          /**
           @param {Element} files
           */
          function loadAllFiles(files) {
            var items = files.getElementsByTagName("file");
            for (var i = 0; i < items.length; i++) {
              var file = items.item(i).textContent;
              console.log(file);
              loadFile(file);
            }
          }

          function enumerate() {

            ajaxAsync(uploaderUri + "/$enumPath", "", function sucess(response){
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
