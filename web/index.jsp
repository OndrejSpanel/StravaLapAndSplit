<%--
  Created by IntelliJ IDEA.
  User: Ondra
  Date: 15.6.2016
  Time: 13:55
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>
<%@ page import="com.google.appengine.api.utils.SystemProperty" %>
<html>
  <head>
    <title>Strava Split And Lap</title>
  </head>
  <body>
  <%
    Boolean localhost = SystemProperty.environment.value() == SystemProperty.Environment.Value.Development;
    String hostname = localhost ? "http://localhost:8080" : "https://strava-lap-and-split.appspot.com/";
    String uri = "https://www.strava.com/oauth/authorize?";
    String action = uri + "client_id=8138&response_type=code&redirect_uri=" + hostname + "/token_exchange.jsp&scope=write,view_private";
  %>
  <a href=<%=action%>>Connect with STRAVA</a>

  <%= Main.someComputation() %>

  </body>
</html>
