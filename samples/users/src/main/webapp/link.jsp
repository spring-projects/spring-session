<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Linked Page</title>
    <link rel="stylesheet" href="assets/bootstrap.min.css">
    <style type="text/css">
        body {
            padding: 1em;
        }
    </style>
</head>
<body>
    <div class="container">
      <!-- Static navbar -->
      <nav class="navbar navbar-default" role="navigation">
        <div class="container-fluid">
          <div class="navbar-header">
            <a class="navbar-brand" href="https://github.com/spring-projects/spring-session/">Spring Session</a>
          </div>
          <div id="navbar" class="navbar-collapse collapse">
            <ul class="nav navbar-nav">
              <c:url value="/" var="homeUrl"/>
              <li><a id="navHome" href="${homeUrl}">Home</a></li>
              <c:url value="/link.jsp" var="linkUrl"/>
              <li class="active"><a id="navLink" href="${linkUrl}">Link</a></li>

            </ul>
            <c:if test="${currentAccount != null or not empty accounts}">
                <ul class="nav navbar-nav navbar-right">
                  <li class="dropdown">
                    <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><c:out value="${username}"/> <span class="caret"></span></a>
                    <ul class="dropdown-menu" role="menu">
                      <c:if test="${currentAccount != null}">
                          <li><a id="logout" href="${currentAccount.logoutUrl}">Log Out</a></li>
                          <li><a id="addAccount" href="${addAccountUrl}">Add Account</a></li>
                      </c:if>
                      <c:if test="${not empty accounts}">
                          <li class="divider"></li>
                          <li class="dropdown-header">Switch Account</li>
                          <li class="divider"></li>
                      </c:if>
                      <c:forEach items="${accounts}" var="account">
                          <li><a id="switchAccount${account.username}" href="${account.switchAccountUrl}"><c:out value="${account.username}"/></a></li>
                      </c:forEach>
                    </ul>
                  </li>
                </ul>
            </c:if>
          </div><!--/.nav-collapse -->
        </div><!--/.container-fluid -->
      </nav>

        <h1>Description</h1>
        <p>This page demonstrates how we keep track of the correct user session between links even when multiple tabs are open. Try opening another tab with a different user and browse. Then come back to the original tab and see the correct user is maintained.</p>

        <c:choose>
            <c:when test="${username == null}">
                <h1>Please Log In</h1>
                <p>You are not currently authenticated with this session. <a href="${homeUrl}">Log In</a></p>
            </c:when>
            <c:otherwise>
                <h1 id="un"><c:out value="${username}"/></h1>
                <p>You are authenticated as <b><c:out value="${username}"/></b>. Observe that you can <a href="${linkUrl}">navigate links</a> and the correct session is used. Using the links in the upper right corner you can:</p>
                <ul>
                    <li>Log Out</li>
                    <li>Switch Accounts</li>
                    <li>Add Account</li>
                   </ul>
            </c:otherwise>
        </c:choose>
    </div>
    <!-- Bootstrap core JavaScript
    ================================================== -->
    <!-- Placed at the end of the document so the pages load faster -->
    <script src="./assets/js/jquery.min.js"></script>
    <script src="./assets/js/bootstrap.min.js"></script>
    <!-- IE10 viewport hack for Surface/desktop Windows 8 bug -->
    <script src="./assets/js/ie10-viewport-bug-workaround.js"></script>
</body>
</html>
