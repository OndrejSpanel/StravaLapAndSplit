<%@ page import="com.github.opengrabeso.stravalas.Main" %><%--
  Created by IntelliJ IDEA.
  User: Ondra
  Date: 16.6.2016
  Time: 19:48
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
  <title>Strava Split And Lap - select activity</title>
  <style>
    tr:nth-child(even) {background-color: #f2f2f2}
    tr:hover {background-color: #f0f0e0}
  </style>
</head>
<body>
<%
  String code = request.getParameter("code");
  Main.StravaAuthResult auth = Main.stravaAuth(code);
  session.setAttribute("authToken", auth.token());
  session.setAttribute("mapboxToken", auth.mapboxToken());
  Main.ActivityId[] activities = Main.lastActivities(auth.token());
%>
<p>Athlete: <b><%= Main.athlete(auth.token())%></b></p>
<table>
  <% for (Main.ActivityId act : activities) {%>
  <tr>
    <td><%=act.id()%> </td>
    <td><%= act.sportName()%> </td>
    <td><a href="<%=act.link()%>"><%=act.name()%></a></td>
    <td><%=Main.displayDistance(act.distance())%> km</td>
    <td><%=Main.displaySeconds(act.duration())%></td>
    <td><form action="activity.jsp" method="get">
        <input type="hidden" name="activityId" value="<%=act.id()%>"/>
        <input type="submit" value=">>"/>
      </form>
    </td>
  </tr>
  <% } %>
</table>
<% if (activities.length > 0) { %>
<form action="activity.jsp" method="get">
<p>Other activity Id: <input type="text" name="activityId" value="<%=activities[0].id()%>"/>
  <input type="submit" value="Submit"/>
</p>
</form>
<% } %>
</body>
</html>
