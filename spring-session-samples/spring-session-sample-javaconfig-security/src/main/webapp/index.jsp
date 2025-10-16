<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
	<title>Secured Content</title>
	<style type="text/css">
		body {
			padding: 1em;
		}
		#un {
			font-weight: bold;
		}
	</style>
</head>
<body>
	<div class="container">
		<h1>Description</h1>
		<p>This demonstrates how Spring Session can be combined with Spring Security. The important thing to ensure is that Spring Session's Filter is included before Spring Security's Filter.</p>

		<h1>Logged in as</h1>

		<p>You are currently logged in as <span id="un"><c:out value="${pageContext.request.remoteUser}"/></span>.</p>

		<c:url value="/logout" var="logoutUrl"/>
		<form action="${logoutUrl}" method="post">
			<input type="submit" value="Log Out"/>
			<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
		</form>
	</div>
</body>
</html>
