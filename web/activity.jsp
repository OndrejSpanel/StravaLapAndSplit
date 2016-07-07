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
  Main.ActivityEvents laps = Main.getEventsFrom(authToken, actId);

%>

<a href="<%= laps.id().link()%>"><%= laps.id().name()%>
</a>

<form action="download.fit" method="post">
  <table border="1">
    <tr>
      <th>Event</th>
      <th>Time</th>
      <th>Distance</th>
    </tr>
    <% for (Main.Event t : laps.events()) { %>
    <tr>
      <td><%= t.kind()%>
      </td>
      <td><%= Main.displaySeconds(t.stamp().time()) %>
      </td>
      <td><%= Main.displayDistance(t.stamp().dist()) %>
      </td>
    </tr>
    <% } %>
  </table>
  <input type="hidden" name="id" value="<%= laps.id().id()%>"/>
  <input type="hidden" name="operation" value="copy"/>
  <input type="submit" value="Download result"/>
</form>

</body>
</html>
