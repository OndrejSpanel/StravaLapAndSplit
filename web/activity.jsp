<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>

<html>

<%
  String authToken = (String) session.getAttribute("authToken");
  String actId = request.getParameter("activityId");
  Main.ActivityEvents laps = Main.getEventsFrom(authToken, actId);
%>

<head>
  <title>Strava Split And Lap</title>

  <script type="text/javascript">
    var id = "<%= actId %>";
    var authToken = "<%= authToken %>";
    var events = [
      <%for (Event t : laps.events()) {%> ["<%= t.defaultEvent() %>", <%=t.id() %>], <% } %>
    ];

    /**
     * @param {Number} id
     * @param {Number} i
     * @return {String}
     */
    function splitLink(id, i) {
      var splitWithEvents =
              '  <input type="hidden" name="id" value="' + id + '"/>' +
              '  <input type="hidden" name="auth_token" value="' + authToken + '"/>' +
              '  <input type="hidden" name="operation" value="split"/>' +
              '  <input type="hidden" name="time" value="' + i + '"/>' +
              '  <input type="submit" value="Download activity at ' + i + '"/>';
      events.forEach( function(e, i) {
        splitWithEvents = splitWithEvents + '<input type="hidden" name="events" value="' + e[0] + '"/>';
      });

      return '<form action="download" method="post">' + splitWithEvents + '</form>';

    }

    function addEvents() {
      var tgt = document.getElementById("output");
      // TODO: remove any existing tr
      events.forEach(function(e){
        if (e[0] == "split") {
          var tr = document.createElement('tr');
          var td = document.createElement('td');
          td.innerHTML = splitLink(id, e[1]);
          tr.appendChild(td);
          tgt.appendChild(tr);
        }
      });
    }

    function cleanEvents() {
      var tgt = document.getElementById("output");
      var chs = Array.prototype.slice.call(tgt.childNodes);
      chs.forEach(function(ch) {
        if (ch.firstChild!=null && ch.firstChild.nodeName=="TD") {
          tgt.removeChild(ch);
        }
      });
    }
    function updateEvents() {
      cleanEvents();
      addEvents();
    }


    /**
     * @param {Element} item
     * @param {String} newValue
     * */
    function changeEvent(item, newValue) {
      var itemTime = item.id;
      events.forEach(function(item, i) { if (item[1] == itemTime) events[i][0] = newValue; });
      updateEvents();
    }
  </script>
</head>

<body>


<a href="<%= laps.id().link()%>"><%= laps.id().name()%>
</a>

<form action ="download" method="post">
  <input type="hidden" name="id" value="<%= laps.id().id()%>"/>
  <input type="hidden" name="auth_token" value="<%= authToken%>"/>
  <input type="hidden" name="operation" value="copy"/>
  <input type="submit" value="Backup original activity"/>
</form>

<form action="download" method="post">
  <table border="1">
    <tr>
      <th>Event</th>
      <th>Time</th>
      <th>Distance</th>
      <th>Event</th>
      <th>Link</th>
    </tr>
    <%
      for (Event t : laps.events()) {
        String split = t.defaultEvent();
    %>
    <tr>
      <td><%= t.description()%>
      </td>
      <td><%= Main.displaySeconds(t.stamp().time()) %></td>
      <td><%= Main.displayDistance(t.stamp().dist()) %></td>
      <td> <%
          EventKind[] types = t.listTypes();
          if (types.length != 1) {
        %>
        <select id="<%=t.stamp().time()%>" name="events" onchange="changeEvent(this, this.options[this.selectedIndex].value)">
            <%
              for (EventKind et : types) {
            %> <option value="<%= et.id()%>"<%= split.equals(et.id()) ? "selected" : ""%>><%= et.display()%></option>
            <% }
          %></select>
        <% } else { %>
          <%= Events.typeToDisplay(types, types[0].id())%>
          <input type="hidden" name = "events" value = "<%= t.defaultEvent() %>"/> <%
        } %>
      </td>
      <td></td>
    </tr>
    <% } %>
  </table>
  <table id="output" border="1">
    <tr>
      <th>Link</th>
    </tr>
  </table>
  <input type="hidden" name="id" value="<%= laps.id().id()%>"/>
  <input type="hidden" name="operation" value="process"/>
  <input type="hidden" name="auth_token" value="<%= authToken%>"/>
  <input type="submit" value="Download result"/>

  <script type="text/javascript">updateEvents()</script>
  <script type="text/javascript">updateEvents()</script>
</form>

</body>
</html>
