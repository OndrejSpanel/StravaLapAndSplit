<%--
  Created by IntelliJ IDEA.
  User: Ondra
  Date: 15.6.2016
  Time: 13:55
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>
<html>
  <head>
    <title>Strava Split And Lap</title>
  </head>
  <body>
  <p>Activity to edit:</p>

  <p>Original laps:</p>
  <p>Laps found:</p>
  <p>Split at:</p>

  <p>Counting to three:</p>
  <% for (int i=1; i<4; i++) { %>
  <p>This number is <%= Main.doComputation(i) %>.</p>
  <% } %>
  <p>OK.</p>

  <%= Main.someComputation() %>

  </body>
</html>
