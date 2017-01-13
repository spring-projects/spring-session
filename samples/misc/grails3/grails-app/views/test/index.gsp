<html>
<head>
  <title>Home Page</title>
</head>
<body>
  <div id="un">
    <sec:loggedInUserInfo field='username'/>
  </div>
  <div id="session">
    ${session.id}
  </div>
  <form action="/logout" method="post">
    <input type="submit" value="Log Out"/>
  </form>
</body>
</html>
