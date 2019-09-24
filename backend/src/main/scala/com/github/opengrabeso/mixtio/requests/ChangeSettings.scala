package com.github.opengrabeso.mixtio
package requests

import Settings._

import scala.xml.NodeSeq

trait ChangeSettings {
  def suuntoSettings(userId: String, settings: SettingsStorage): NodeSeq = {
    <script src="static/timeUtils.js"></script>
    <script src="static/ajaxUtils.js"></script>
    <script>{xml.Unparsed(
      //language=JavaScript
      """
      function currentQuestTime(d) {
        var offset = parseInt(document.getElementById("quest_time_offset").value);
        return formatTimeSec(new Date(d.getTime() + 1000*offset));
      }
      function updateClock() {
        var d = new Date();
        document.getElementById("time").innerHTML = formatTimeSec(d);
        document.getElementById("timeQuest").innerHTML = currentQuestTime(d);
      }
      function startUpdatingClock() {
        updateClock();
        setTimeout(function () {
          startUpdatingClock();
        }, 500);
      }
      """)}
    </script>
    <h2>Settings</h2>
    <table>
      <tr><td>
        Max HR</td><td><input type="number" name="max_hr" id="max_hr" min="100" max="260" value={settings.maxHR.toString} onchange={s"settingsChanged(this.id,this.value,'$userId')"}></input>
      </td></tr>
      <tr><td>
        Quest time offset</td><td> <input type="number" id="quest_time_offset" name="quest_time_offset" min="-60" max="60" value={settings.questTimeOffset.toString} onchange={s"settingsChanged(this.id,this.value,'$userId');updateClock()"}></input>
      </td>
        <td>Adjust up or down so that Quest time below matches the time on your watch</td>
      </tr>

      <tr>
        <td>Current time</td>
        <td id="time"></td>
      </tr>
      <tr>
        <td>Quest time</td>
        <td><b id="timeQuest"></b></td>
      </tr>
    </table>
    <script>
      startUpdatingClock();
    </script>
  }
}
