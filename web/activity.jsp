<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.github.opengrabeso.stravalas.*" %>

<html>

<%
  String authToken = (String) session.getAttribute("authToken");
  String actId = request.getParameter("activityId");
  Main.ActivityEvents laps = Main.getEventsFrom(authToken, actId);
%>

<head>
  <title>Strava Split And Lap</title>

  <script type="text/javascript">
    var id = "<%= actId %>";
    var authToken = "<%= authToken %>";
    var events = [
      <%for (Event t : laps.events()) {%> ["<%= t.defaultEvent() %>", <%=t.id() %>], <% } %>
    ];

    /**
     * @param {String} id
     * @param {String} time
     * @return {String}
     */
    function splitLink(id, time) {
      var splitWithEvents =
              '  <input type="hidden" name="id" value="' + id + '"/>' +
              '  <input type="hidden" name="auth_token" value="' + authToken + '"/>' +
              '  <input type="hidden" name="operation" value="split"/>' +
              '  <input type="hidden" name="time" value="' + time + '"/>' +
              '  <input type="submit" value="Download activity at ' + time + '"/>';

      events.forEach( function(e) {
        splitWithEvents = splitWithEvents + '<input type="hidden" name="events" value="' + e[0] + '"/>';
      });

      return '<form action="download" method="post">' + splitWithEvents + '</form>';

    }

    function initEvents() {
      events.forEach(function(e){
        if (e[0] == "split") {
          addEvent(e[1]);
        }
      });
    }

    /** @param {String} time */
    function addEvent(time) {
      var tableLink = document.getElementById("link" + time);
      tableLink.innerHTML = splitLink(id, time);
    }

    /** @param {String} time */
    function removeEvent(time) {
      var tableLink = document.getElementById("link" + time);
      tableLink.innerHTML = "";
    }

    /**
     * @param {Element} item
     * @param {String} newValue
     * */
    function changeEvent(item, newValue) {
      var itemTime = item.id;
      events.forEach(function(item, i) { if (item[1] == itemTime) events[i][0] = newValue; });
      if (newValue=="split") {
        addEvent(itemTime);
      } else {
        removeEvent(itemTime);
      }
      initEvents();
    }
  </script>
</head>

<body>


<form action ="download" method="post">
  <input type="hidden" name="id" value="<%= laps.id().id()%>"/>
  <input type="hidden" name="auth_token" value="<%= authToken%>"/>
  <input type="hidden" name="operation" value="copy"/>
  <input type="submit" value="Backup original activity"/>
</form>

  <table border="1">
    <tr>
      <th>Event</th>
      <th>Time</th>
      <th>Distance</th>
      <th>Event</th>
      <th>Link</th>
    </tr>
    <%
      for (Event t : laps.events()) {
        String split = t.defaultEvent();
    %>
    <tr>
      <td><%= t.description()%>
      </td>
      <td><%= Main.displaySeconds(t.stamp().time()) %></td>
      <td><%= Main.displayDistance(t.stamp().dist()) %></td>
      <td> <%
          EventKind[] types = t.listTypes();
          if (types.length != 1) {
        %>
        <select id="<%=t.stamp().time()%>" name="events" onchange="changeEvent(this, this.options[this.selectedIndex].value)">
            <%
              for (EventKind et : types) {
            %> <option value="<%= et.id()%>"<%= split.equals(et.id()) ? "selected" : ""%>><%= et.display()%></option>
            <% }
          %></select>
        <% } else { %>
          <%= Events.typeToDisplay(types, types[0].id())%>
          <input type="hidden" name = "events" value = "<%= t.defaultEvent() %>"/> <%
        } %>
      </td>
      <td id="link<%=t.stamp().time()%>"></td>
    </tr>
    <% } %>
  </table>
  <form action="download" method="post">
  <input type="hidden" name="id" value="<%= laps.id().id()%>"/>
  <input type="hidden" name="operation" value="process"/>
  <input type="hidden" name="auth_token" value="<%= authToken%>"/>
  <input type="submit" value="Download result"/>
  </form>

  <script type="text/javascript">initEvents()</script>

</body>
</html>
