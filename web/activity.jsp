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
    var events = [
      <%for (Event t : laps.events()) {%> "<%= t.defaultEvent() %>", <% } %>
    ];

    function addEvents() {
      var tgt = document.getElementById("output");
      var tr = document.createElement('tr');
      var td = document.createElement('td');
      var text1 = document.createTextNode('Text1');
      // TODO: remove any existing tr
      tr.appendChild(td);
      td.appendChild(text1);
      tgt.appendChild(tr);
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
        <select name="events">
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
