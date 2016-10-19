package com.github.opengrabeso.stravalas
package requests

import spark.{Request, Response}

@Handle("/")
object IndexHtml extends DefineRequest {
  def html(request: Request, resp: Response) = {
    <html>
      <head>
        <title>Strava Split And Lap</title>
      </head>
      <body>
        {val hostname = request.host()
      val scheme = request.scheme()
      val secret = Main.secret
      val clientId = secret.appId
      val serverUri = scheme + "://" + hostname // Spark hostname seems to include port if needed
      val uri = "https://www.strava.com/oauth/authorize?"
      val action = uri + "client_id=" + clientId + "&response_type=code&redirect_uri=" + serverUri + "/selectActivity&scope=write,view_private"
      <h3>Work in progress, use at your own risk.</h3>
        <p>
          This tool allows you to split activity or edit lap information for it.
          It automatically detects places where you have stopped and allows you to create a split or lap there.
        </p>
        <h4>Working</h4>
        <ul>
          <li>Pauses listed, suggest laps and splits</li>
          <li>User can select events where laps should be inserted</li>
          <li>User can split activity and download individual parts</li>
          <li>Show average speed / tempo, autodetect activity type</li>
        </ul>
        <h4>Planned (not working yet)</h4>
        <ul>
          <li>User can change activity type (Run / Ride / Swim)</li>
          <li>Map (using MapBox)</li>
        </ul>
        <h4>Considering to add later</h4>
        <ul>
          <li>Delete old activity and upload the new version</li>
        </ul>
        <p>
          <i>
            Note: the original activity needs to be deleted in the process, therefore you will lose any comments and kudos you already have on it and your achievements will be recomputed.
          </i>
        </p> :+ {
        if (!clientId.isEmpty) {
          <a href={action}>
            <img src="static/ConnectWithStrava.png" alt="Connect with STRAVA"></img>
          </a>
        } else {
          <p>Error:
            {secret.error}
          </p>
        }
      }}
      </body>
    </html>
  }
}