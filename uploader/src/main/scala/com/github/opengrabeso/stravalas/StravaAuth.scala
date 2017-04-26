package com.github.opengrabeso.stravalas

import java.awt.Desktop
import java.io.IOException
import java.net.URL

import akka.actor.{Actor, ActorSystem, Props, ReceiveTimeout, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try
import scala.xml.Elem

object StravaAuth {
  private val callbackPath = "stravaAuth.html"
  private val statusPath = "status.xml"
  private val donePath = "done.xml"

  private val pollPeriod = 2000 // miliseconds

  val system = ActorSystem("UploadSystem")

  case object ServerStatusSent
  case object ServerDoneSent
  case object WindowClosedSent
  case object UploadDone
  case object UploadNotDone

  private val authResult = Promise[String]()

  case class UploadId(filename: String)

  case class Report(progress: String = "Uploading files...", result: String = "", uploadedIds: List[UploadId] = Nil)

  private var report = Report()

  private var session: String = ""

  class PollUntilTerminated extends Actor {

    private var done = false

    def pollUntilTerminated(last: Boolean = false): Unit = {
      val timeoutDelay = pollPeriod * (if (last) 3 else 20)
      context.setReceiveTimeout(timeoutDelay.millisecond)
      println(s"pollUntilTerminated $timeoutDelay")
    }

    override def receive = {
      case UploadDone =>
        done = true

      case UploadNotDone =>
        done = false

      case (ReceiveTimeout | WindowClosedSent) =>
        if (!done) {
          println("Browser window closed.")
          pollUntilTerminated(true)
        } else {
          println("Browser window closed, all done, terminating web server.")
          context.stop(self)
        }
      case ServerDoneSent =>
        println("Final status displayed.")
        pollUntilTerminated(true)
      case ServerStatusSent => //
        println("ServerStatusSent")
        pollUntilTerminated()
      case x =>
        println(x)
        pollUntilTerminated()
    }
  }

  //noinspection FieldFromDelayedInit
  val timeoutActor = system.actorOf(Props[PollUntilTerminated], "PollUntilTerminated")

  object HttpHandlerHelper {
    private def sendResponseWithContentType(code: Int, responseXml: Elem, ct: WithCharset) = {
      HttpResponse(status = code, entity = HttpEntity(ct, responseXml.toString))
    }

    def sendResponse(code: Int, responseXml: Elem): HttpResponse = {
      sendResponseWithContentType(code, responseXml, ContentTypes.`text/html(UTF-8)`)
    }

    def sendResponseXML(code: Int, responseXml: Elem): HttpResponse = {
      sendResponseWithContentType(code, responseXml, ContentTypes.`text/xml(UTF-8)`)
    }

    def respondAuthSuccess(state: String): HttpResponse = {
      //noinspection JSUnusedGlobalSymbols
      val scriptText =
      //language=JavaScript
        s"""
        var finished = false;

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


        function updateStatus() {
          setTimeout(function () {
            var xmlhttp = ajax();
            // the callback function to be callled when AJAX request comes back
            xmlhttp.onreadystatechange = function () {
              if (xmlhttp.readyState === 4) {
                if (xmlhttp.status >= 200 && xmlhttp.status < 300) {
                  console.log(xmlhttp);
                  var response = xmlhttp.responseXML.getElementsByTagName("html")[0];
                  document.getElementById("myDiv").innerHTML = response.innerHTML;
                  updateStatus(); // schedule recursively another update
                } else {
                  finished = true;
                  document.getElementById("myDiv").innerHTML = "<h3>Application not responding</h3>";
                }
              }
            };
            ajaxPost(xmlhttp, "./$statusPath?state=$state", true); // POST to prevent caching
          }, $pollPeriod)
        }

        function closingCode(){
          if (!finished) {
            var xmlhttp = ajax();
            ajaxPost(xmlhttp, "./$donePath?state=$state", false); // sync to make sure request is send before the window closes
            return null;
          }
        }

        function ajaxPost(/** XMLHttpRequest */ xmlhttp, /** string */ request, /** boolean */ async) {
          xmlhttp.open("POST", request, async); // POST to prevent caching
          xmlhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
          xmlhttp.send("");

        }
        """

      val responseXml = <html>
        <head>
          <script type="text/javascript">
            {scala.xml.Unparsed(scriptText)}
          </script>
        </head>

        <title>StravaMat uploader</title>
        <body>
          <p>Automated upload application authenticated to Strava</p>

          <div id="myDiv">
            <h3>Starting processing...</h3>
          </div>

        </body>
        <script>
          updateStatus()
          updateClock()
          window.onbeforeunload = closingCode;
        </script>
      </html>

      sendResponse(200, responseXml)
    }

    def respondFailure(error: String): HttpResponse = {
      val responseXml =
        <html>
          <title>Suunto To Strava Authentication</title>
          <body>
            <h1>Suunto To Strava Authenticated</h1>
            <p>This window has expired.<br/>You may have opened another window?<br/>
              Error: {error}
            </p>
            <p>Proceed to:
              <br/>
              <a href="https://www.strava.com">Strava</a> <br/>
            </p>
          </body>
        </html>

      sendResponse(400, responseXml)
    }

    def respondAuthFailure(error: String): HttpResponse = {
      val responseXml =
        <html>
          <title>Suunto To Strava Authentication</title>
          <body>
            <h1>Suunto To Strava Not Authenticated</h1>
            <p>Suunto To Strava automated upload application not authenticated to Strava.<br/>
              Error: {error}
            </p>
            <p>Proceed to:
              <br/>
              <a href="https://www.strava.com">Strava</a> <br/>
              <a href="https://www.strava.com/settings/apps">Check Strava apps settings</a>
            </p>
          </body>
        </html>

      sendResponse(400, responseXml)
    }
  }

  def statusHandler(state: String): HttpResponse = {
    if (session == state) {
      if (report.result.nonEmpty) {

        val response =
          <html>
            <h3> {report.result} </h3>
            <p>Proceed to:
              <br/>
              <a href="https://www.strava.com">Strava</a> <br/>
              <a href="https://www.strava.com/athlete/training">My Activities</a> <br/>
            </p>
          </html>

        timeoutActor ! ServerDoneSent
        // send redirect

        HttpHandlerHelper.sendResponseXML(200, response)
      } else {
        val response = <html> <h3> {report.progress} </h3> </html>
        timeoutActor ! ServerStatusSent
        HttpHandlerHelper.sendResponseXML(202, response)
      }
    } else {
      val response = <error>Invalid session id</error>
      timeoutActor ! ServerStatusSent
      HttpHandlerHelper.sendResponseXML(400, response)

    }
  }
  def doneHandler(state: String): HttpResponse = {
    if (session == state) {
      // the session is closed, report to the server
      timeoutActor ! WindowClosedSent
    }
    HttpHandlerHelper.respondFailure("Session closed")
  }

  def authHandler(state: String, code: Option[String], error: Option[String]): HttpResponse = {
    if (session == "" || state == session) {
      (code, error) match {
        case (Some(c), _) =>
          session = state
          if (!authResult.isCompleted) authResult.success(c)
          else timeoutActor ! ServerStatusSent
          HttpHandlerHelper.respondAuthSuccess(state)
        case (_, Some(e)) =>
          authResult.failure(new IllegalArgumentException(s"Auth error $error"))
          HttpHandlerHelper.respondAuthFailure(e)
        case _ =>
          HttpHandlerHelper.respondAuthFailure("Unexpected response, expected code or error")
      }
    } else {
      HttpHandlerHelper.respondFailure("Session expired")
    }
  }

  class TimeoutTerminator(server: ServerShutdown) extends Actor {

    context.watch(timeoutActor)

    def receive = {
      case Terminated(_) =>
        println("Poll actor terminated")
        // we do not need a CountDownLatch, as Await on the promise makes sure the response serving has already started
        stopServer(server)
        context.stop(self)
        context.system.terminate()
        println("Terminated actor system")
    }
  }

  case class ServerShutdown(binding: Future[ServerBinding], system: ActorSystem)

  // http://stackoverflow.com/a/3732328/16673
  private def startHttpServer(callbackPort: Int): ServerShutdown = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future map/flatmap in the end

    val route = get {
      path(callbackPath) {
        parameters('state, 'code.?, 'error.?) { (state, code, error) =>
          complete(authHandler(state, code, error))
        }
      }
    } ~ post {
      path(statusPath) {
        parameters('state) { state =>
          complete(statusHandler(state))
        }
      } ~  path(donePath) {
        parameters('state) { state =>
          complete(doneHandler(state))
        }
      }
    }

    // `route` will be implicitly converted to `Flow` using `RouteResult.route2HandlerFlow`
    // IDEA 2016.3 currently does not follow, to prevent error highligh we use explicit handlerFlow
    val bindingFuture = Http().bindAndHandle(Route.handlerFlow(route), "localhost", callbackPort)

    val server = ServerShutdown(bindingFuture, system)

    //noinspection FieldFromDelayedInit
    system.actorOf(Props(classOf[TimeoutTerminator], server), "TimeoutTerminator")

    server

  }

  def stopServer(server: ServerShutdown): Unit = {
    implicit val executionContext = server.system.dispatcher
    server.binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => server.system.terminate()) // and shutdown when done
  }

  def apply(appId: Int, callbackPort: Int, access: String): Option[String] = {

    startHttpServer(callbackPort)

    val sessionId = System.currentTimeMillis().toHexString
    val callbackUrl = s"http://localhost:$callbackPort/$callbackPath"
    val forcePrompt = false // useful for debugging / troubleshooting
    val forceStr = if (forcePrompt) "&approval_prompt=force" else ""
    val url = s"https://www.strava.com/oauth/authorize?client_id=$appId&scope=$access&response_type=code&redirect_uri=$callbackUrl&state=$sessionId$forceStr"
    try {
      Desktop.getDesktop.browse(new URL(url).toURI)
    } catch {
      case e: IOException =>
        e.printStackTrace()
    }

    val ret = Try (Await.result(authResult.future, 5.minutes)).toOption

    ret
  }

  def progress(status: String): Unit = {
    println(s"Progress: $status")
    report = report.copy(progress = status)
  }

  def stop(status: String, uploaded: List[UploadId]): Unit = {

    timeoutActor ! UploadDone

    report = report.copy(result = status, uploadedIds = uploaded)
  }

}
