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
  <form action="activity.jsp" method="POST">
    <p>Activity to edit:  <input type="text" name="activityId"/>
      <input type="submit" value="Submit" />
    </p>
  </form>

  <%= Main.someComputation() %>

  </body>
</html>
