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
  <%
    String hostname = request.getServerName();
    int port = request.getServerPort();
    String scheme = request.getScheme();
    String clientId = Main.secret()._1;

    String serverUri = scheme + "://" + hostname + (port != 80 ? String.format(":%d", port) : "");
    String uri = "https://www.strava.com/oauth/authorize?";
    String action = uri + "client_id=" + clientId + "&response_type=code&redirect_uri=" + serverUri + "/selectActivity.jsp&scope=write,view_private";
  %>
  <h3>Work in progress, not doing anything usefull yet</h3>
  <p>
    This tool allows you to split activity or edit lap information for it.
    It automatically detects places where you have stopped and allows you to create a split or lap there.
  </p>
  <p>
    <i>Note: the original activity needs to be deleted in the process, therefore you will lose any comments and kudos on it.</i>
  </p>
  <a href=<%=action%>><img src="static/ConnectWithStrava.png" alt="Connect with STRAVA"/></a>


  </body>
</html>
