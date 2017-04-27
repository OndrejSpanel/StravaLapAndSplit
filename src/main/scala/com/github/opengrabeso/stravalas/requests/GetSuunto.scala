package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

object GetSuunto extends DefineRequest("/getSuunto", method = Method.Get) with ActivityRequestHandler {
  override def html(request: Request, resp: Response) = {
    val session = request.session
    val auth = session.attribute[Main.StravaAuthResult]("auth")

    val uploaderUri = "http://localhost:8088" // uploader should be running as a local web server
    val enumPath = "enum" // must be the same as in StravamatUploader // TODO: share sources

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

        <p>
        <div id="myDiv"></div>
        </p>
        {bodyFooter}
        <script>{xml.Unparsed(
          //language=JavaScript
          s"""
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

          function ajaxPost(/** XMLHttpRequest */ xmlhttp, /** string */ request, /** boolean */ async) {
            xmlhttp.open("POST", request, async); // POST to prevent caching
            xmlhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
            xmlhttp.send("");

          }

          function enumerate() {
            var xmlhttp = ajax();
            // the callback function to be callled when AJAX request comes back
            xmlhttp.onreadystatechange = function () {
              if (xmlhttp.readyState === 4) {
                if (xmlhttp.status >= 200 && xmlhttp.status < 300) {
                  //console.log(xmlhttp);
                  var response = xmlhttp.responseXML;
                  document.getElementById("myDiv").innerText = new XMLSerializer().serializeToString(response.documentElement);
                } else {
                  document.getElementById("myDiv").innerHTML = "<h3>Uploader not responding</h3>";
                }
              }
            };
            ajaxPost(xmlhttp, "$uploaderUri/$enumPath", true); // POST to prevent caching
          }

          window.onload = enumerate();
          """
        )}
        </script>
      </body>
    </html>
  }
}
