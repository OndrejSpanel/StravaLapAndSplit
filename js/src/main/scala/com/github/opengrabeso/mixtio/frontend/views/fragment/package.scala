package com.github.opengrabeso.mixtio
package frontend
package views

import io.udash.css._

package object fragment {
  def header(name: String, id: String) = {
    <div id="header" style="background-color:#fca;overflow:auto">
      <table>
        <tr>
          <td>
            <a href="/"><img src="static/stravaUpload64.png"></img></a>
          </td>
          <td>
            <table>
              <tr>
                <td>
                  <a href="/">{appName}</a>
                </td>
              </tr>
              <tr>
                <td>
                  Athlete:
                  <a href={s"https://www.strava.com/athletes/$id"}>
                    {name}
                  </a>
                </td>
              </tr>
            </table>
          </td>
          <td>
            <form action={"logout"}>
              <input type="submit" value ="Log Out"/>
            </form>
          </td>
        </tr>
      </table>
    </div>
      <p></p>
  }


  def footer = {
    <p></p>
      <div id="footer" style="background-color:#fca;overflow:auto">
        <a href="http://labs.strava.com/" id="powered_by_strava" rel="nofollow">
          <img align="left" src="static/api_logo_pwrdBy_strava_horiz_white.png" style="max-height:46px"/>
        </a>
        <p style="color:#fff"><a href="https://darksky.net/poweredby/" style="color:#fff">Powered by Dark Sky</a> © 2016 - 2018 <a href={s"https://github.com/OndrejSpanel/$gitHubName"} style="color:inherit">Ondřej Španěl</a></p>
        <div/>
      </div>
  }


}
