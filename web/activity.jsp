<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>

<html>
<head>
  <title>Strava Split And Lap</title>
</head>

<body>

<%
  String authToken = (String) session.getAttribute("authToken");
  String actId = request.getParameter("activityId");
  Main.ActivityLaps laps = Main.getLapsFrom(authToken, actId);

%>

<a href="<%= laps.id().link()%>"><%= laps.id().name()%>
</a>

<p>Original laps:</p>
<table border="1">
  <% for (int t : laps.laps()) { %>
  <tr>
    <td>Lap time <%= Main.displaySeconds(t) %>.</td>
  </tr>
  <% } %>
</table>
<p>Pauses:</p>
<table border="1">
  <% for (int t : laps.pauses()) { %>
  <tr>
    <td>Pause time <%= Main.displaySeconds(t) %>.</td>
  </tr>
  <% } %>
</table>

</body>
</html>
