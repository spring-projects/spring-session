<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
	<title>Session Attributes</title>
	<link rel="stylesheet" href="assets/bootstrap.min.css">
	<style type="text/css">
		body {
			padding: 1em;
		}
	</style>
</head>
<body>
	<div class="container">
		<h1>Description</h1>
		<p>This application demonstrates how to use a GemFire instance to back your session. Notice that there is no JSESSIONID cookie. We are also able to customize the way of identifying what the requested session id is.</p>

		<h1>Try it</h1>

		<form class="form-inline" role="form" action="./session" method="post">
			<label for="attributeName">Attribute Name</label>
			<input id="attributeName" type="text" name="attributeName"/>
			<label for="attributeValue">Attribute Value</label>
			<input id="attributeValue" type="text" name="attributeValue"/>
			<input type="submit" value="Set Attribute"/>
		</form>

		<hr/>

		<table class="table table-striped">
			<thead>
			<tr>
				<th>Attribute Name</th>
				<th>Attribute Value</th>
			</tr>
			</thead>
			<tbody>
			<c:forEach items="${sessionScope}" var="attr">
				<tr>
					<td><c:out value="${attr.key}"/></td>
					<td><c:out value="${attr.value}"/></td>
				</tr>
			</c:forEach>
			</tbody>
		</table>
	</div>
</body>
</html>
