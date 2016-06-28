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
  <title>Strava Split And Lap - authentication</title>
</head>
<body>
<%
  String code = request.getParameter("code");
  String authToken = Main.stravaAuth(code);
  session.setAttribute("authToken", authToken);
%>
<form action="activity.jsp" method="get">
  <p>Athlete: <b><%= Main.athlete(authToken)%></b></p>
  <p>Activity ID: <input type="text" name="activityId" value=""/>
    <input type="submit" value="Submit"/>
  </p>
</form>
</body>
</html>
