<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>
<%@ page import="java.util.Objects" %>

<html>
<head>
  <title>Strava Split And Lap</title>
</head>

<body>

<%
  String authToken = (String) session.getAttribute("authToken");
  String actId = request.getParameter("activityId");
  Main.ActivityEvents laps = Main.getEventsFrom(authToken, actId);

%>

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
    </tr>
    <%
      for (Event t : laps.events()) {
        String split = t.defaultEvent();
    %>
    <tr>
      <td><%= t.description()%>
      </td>
      <td><%= Main.displaySeconds(t.stamp().time()) %>
      </td>
      <td><%= Main.displayDistance(t.stamp().dist()) %>
      </td>
      <td>
        <select name="events">
          <%
            EventKind[] types = t.listTypes();
            for (EventKind et : types) { %>
            <option value="<%= et.id()%>" <%= split.equals(et.id()) ? "selected" : ""%> > <%= et.display()%> </option>
          <% } %>

        </select>
      </td>
    </tr>
    <% } %>
  </table>
  <input type="hidden" name="id" value="<%= laps.id().id()%>"/>
  <input type="hidden" name="operation" value="process"/>
  <input type="hidden" name="auth_token" value="<%= authToken%>"/>
  <input type="submit" value="Download result"/>
</form>

</body>
</html>
