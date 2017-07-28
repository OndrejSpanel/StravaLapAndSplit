package com.github.opengrabeso.stravalas
package requests

import com.github.opengrabeso.stravalas.Main.ActivityEvents
import net.suunto3rdparty.moveslink.MovesLinkUploader
import spark.{Request, Response, Session}
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import com.google.appengine.api.taskqueue._
import net.suunto3rdparty.Util._
import net.suunto3rdparty.strava.StravaAPI

import scala.util.{Failure, Success}

object Process extends DefineRequest.Post("/process") {
  override def html(request: Request, resp: Response) = {

    val session = request.session()
    implicit val auth = session.attribute[Main.StravaAuthResult]("auth")

    val fif = new DiskFileItemFactory()
    fif.setSizeThreshold(1 * 1024) // we do not expect any files, only form parts

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext

      def next() = items.next
    }

    val ops = itemsIterator.flatMap { item =>
      if (item.isFormField) {
        // expect field name id={FileId}
        val IdPattern = "id=(.*)".r
        val id = item.getFieldName match {
          case IdPattern(idText) =>
            Some(FileId.parse(idText))
          case _ =>
            None
        }
        /*
        //println(item)
        val is = item.openStream()
        val itemContent = try {
          IOUtils.toString(is)
        } finally {
          is.close()
        }
        */
        id
      } else {
        None
      }
    }.toVector

    // TODO: create groups, process each group separately
    val toMerge = ops.flatMap { op =>
      Storage.load[Main.ActivityEvents](Main.namespace.stage, op.filename, auth.userId)
    }


    if (toMerge.nonEmpty) {

      val (gpsMoves, attrMovesRaw) = toMerge.partition(_.hasGPS)

      val timeOffset = net.suunto3rdparty.Settings(auth.userId).questTimeOffset
      val ignoreDuration = 30

      val attrMoves = attrMovesRaw.map(_.timeOffset(-timeOffset))

      def filterIgnored(x : ActivityEvents) = x.isAlmostEmpty(ignoreDuration)

      val timelineGPS = gpsMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)
      val timelineAttr = attrMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)

      val merged = MovesLinkUploader.processTimelines(timelineGPS, timelineAttr)

      // store everything into a session storage, and make background tasks to upload it to Strava


      // [START addQueue]
      val queue = QueueFactory.getDefaultQueue
      for (upload <- merged) {

        val sync = false
        if (sync) {
          // sync version - export and upload here

          val export = FitExport.export(upload)

          val api = new StravaAPI(auth.token)
          //val ret = api.uploadRawFileGz(export, "fit.gz")
          val ret = api.uploadRawFile(export, "fit")

          ret match {
            case Failure(_) =>
              println("Upload not started")
            case Success(uploadId) =>
              println(s"Upload started: $uploadId")
          }

        } else {
          // export here, or in the worker? Both is possible

          // filename is not strong enough guarantee of uniqueness, timestamp should be (in single user namespace)
          val uniqueName = upload.id.id.filename + "_" + System.currentTimeMillis().toString
          // are any metadata needed?
          Storage.store(Main.namespace.upload, uniqueName, auth.userId, upload)

          // using post with param is not recommended, but it should be OK when not using any payload
          queue add TaskOptions.Builder.withPayload(UploadResultToStrava(uniqueName, auth))
          println(s"Queued task $uniqueName")
        }
      }


      <html>
        <head>
          <title>Stravamat</title>
          <script src="static/ajaxUtils.js"></script>
        </head>
        <body>
          <table id="uploaded">
          </table>
          <script>{xml.Unparsed(
            // language=JavaScript
            """
            function extractResult(node, tagName, callback) {
              var n = node.getElementsByTagName(tagName);
              if (n.length > 0) return callback(n[0].textContent);
            }
            function showResults() {

              ajaxAsync("check-upload-status", "", function(response) {
                var results = response.documentElement.getElementsByTagName("result");
                var tableBody = document.getElementById("uploaded");
                for (var i = 0; i < results.length; i++) {
                  var tr = document.createElement('TR');
                  var td = document.createElement('TD');

                  var res = extractResult(results[i], "done", function(text) {
                    return "Done " + text;
                  }) || extractResult(results[i], "duplicate", function(text) {
                    return "Duplicate " + text;
                  })|| extractResult(results[i], "error", function(text) {
                    return "Error " + text;
                  });
                  td.appendChild(document.createTextNode(res));
                  tr.appendChild(td);
                  tableBody.appendChild(tr);
                }
                setTimeout(showResults, 1000);
              }, function (failure) {
                console.log(failure);
                setTimeout(showResults, 1000);
              });
            }

            showResults();
            """)}
          </script>
        </body>
      </html>
    }
    else {
      <html>
        <head>
          <title>Stravamat</title>{headPrefix}
        </head>
        <body>
          Empty - no activity selected
        </body>
      </html>
    }
  }

}