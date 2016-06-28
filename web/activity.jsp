<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>

<html>
<head>
  <title>Strava Split And Lap</title>
</head>

<body>

<%
  String authToken = (String)session.getAttribute("authToken");
  String actId = request.getParameter("activityId");
  Main.ActivityLaps laps = Main.getLapsFrom(authToken, actId);

%>

<a href="<%= laps.id().link()%>"><%= laps.id().name()%></a>

<p>Original laps:</p>
<% for (int t : laps.laps()) { %>
<p>Lap time <%= Main.displaySeconds(t) %>.</p>
<% } %>
<p>Pauses:</p>
<% for (int t : laps.pauses()) { %>
<p>Pause time <%= Main.displaySeconds(t) %>.</p>
<% } %>

</body>
</html>
