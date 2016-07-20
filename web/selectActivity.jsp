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
</head>
<body>
<%
  String code = request.getParameter("code");
  String authToken = Main.stravaAuth(code);
  session.setAttribute("authToken", authToken);
  Main.ActivityId[] activities = Main.lastActivities(authToken);
%>
<form action="activity.jsp" method="get">
  <p>Athlete: <b><%= Main.athlete(authToken)%></b></p>
  <% for (Main.ActivityId act: activities ) {%>
  <p> <%=act.id()%> <a href="<%=(act.link())%>"><%=act.name()%></a></p>
  <% } %>
  <% if (activities.length>0) { %>
  <p>Activity ID: <input type="text" name="activityId" value="<%=activities[0].id()%>"/>
    <input type="submit" value="Submit"/>
  </p>
  <% } %>
</form>
</body>
</html>
