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

  def mergeAndUpload(auth: Main.StravaAuthResult, toMerge: Vector[ActivityEvents], sessionId: Long): Int = {
    if (toMerge.nonEmpty) {

      val (gpsMoves, attrMovesRaw) = toMerge.partition(_.hasGPS)

      val timeOffset = net.suunto3rdparty.Settings(auth.userId).questTimeOffset
      val ignoreDuration = 30

      val attrMoves = attrMovesRaw.map(_.timeOffset(-timeOffset))

      def filterIgnored(x: ActivityEvents) = x.isAlmostEmpty(ignoreDuration)

      val timelineGPS = gpsMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)
      val timelineAttr = attrMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)

      val merged = MovesLinkUploader.processTimelines(timelineGPS, timelineAttr)

      // store everything into a session storage, and make background tasks to upload it to Strava

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
          Storage.store(Main.namespace.upload(sessionId), uniqueName, auth.userId, upload)

          // using post with param is not recommended, but it should be OK when not using any payload
          queue add TaskOptions.Builder.withPayload(UploadResultToStrava(uniqueName, auth, sessionId))
          println(s"Queued task $uniqueName")
        }
      }
      merged.size
    } else 0
  }


  def uploadResultsHtml() = {
    <table id="uploaded"></table>
    <script>
      {xml.Unparsed(
      // language=JavaScript
      """
      function extractResult(node, tagName, callback) {
        var n = node.getElementsByTagName(tagName);
        if (n.length > 0) return callback(n[0].textContent);
      }
      function addRow(tableBody, text) {
        var tr = document.createElement('TR');
        var td = document.createElement('TD');
        td.innerHTML = text;
        tr.appendChild(td);
        tableBody.appendChild(tr);
      }
      function showResults() {

        ajaxAsync("check-upload-status", "", function(response) {
          var results = response.documentElement.getElementsByTagName("result");
          var complete = response.documentElement.getElementsByTagName("complete");
          var tableBody = document.getElementById("uploaded");
          for (var i = 0; i < results.length; i++) {

            var res = extractResult(results[i], "done", function(text) {
              // TODO: get Strava user friendly name, or include a time?
              return "Done <a href=https://www.strava.com/activities/" + text + ">" + text + "</a>";
            }) || extractResult(results[i], "duplicate", function(text) {
              if (text ==0) {
                return "Duplicate";
              } else {
                return "Duplicate of <a href=https://www.strava.com/activities/" + text + ">" + text + "</a>";
              }
            }) || extractResult(results[i], "error", function(text) {
              return "Error " + text;
            });
            if (res) addRow(tableBody, res);
          }
          if (complete.length == 0) {
            setTimeout(showResults, 1000);
          } else {
            addRow(tableBody, '<b>Complete</b>'); // TODO: bold
          }
        }, function (failure) {
          console.log(failure);
          setTimeout(showResults, 1000);
        });
      }

      """)}
    </script>
  }

  override def html(request: Request, resp: Response) = {

    val session = request.session()
    implicit val auth = session.attribute[Main.StravaAuthResult]("auth")
    var sessionId = session.attribute[java.lang.Long]("sid").toLong

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
        // a single field upload-id
        val id = item.getFieldName match {
          case IdPattern(idText) =>
            Some(FileId.parse(idText))
          case _ =>
            None
        }
        id
      } else {
        None
      }
    }.toVector

    val toMerge = ops.flatMap { op =>
      Storage.load[ActivityEvents](Main.namespace.stage, op.filename, auth.userId)
    }


    val uploadCount = mergeAndUpload(auth, toMerge, sessionId)

    // used in AJAX only - XML response
    <upload>
      <count>{uploadCount.toString}</count>
    </upload>
  }
}