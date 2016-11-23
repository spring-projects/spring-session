/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.web.http;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The default implementation of {@link CookieSerializer}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.1
 */
public class DefaultCookieSerializer implements CookieSerializer {

	private String cookieName = "SESSION";

	private Boolean useSecureCookie;

	private boolean useHttpOnlyCookie = isServlet3();

	private String cookiePath;

	private int cookieMaxAge = -1;

	private String domainName;

	private Pattern domainNamePattern;

	private String jvmRoute;

	private boolean useBase64Encoding;

	private String rememberMeRequestAttribute;

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.session.web.http.CookieSerializer#readCookieValues(javax.
	 * servlet.http.HttpServletRequest)
	 */
	public List<String> readCookieValues(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		List<String> matchingCookieValues = new ArrayList<String>();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (this.cookieName.equals(cookie.getName())) {
					String sessionId = this.useBase64Encoding
							? base64Decode(cookie.getValue()) : cookie.getValue();
					if (sessionId == null) {
						continue;
					}
					if (this.jvmRoute != null && sessionId.endsWith(this.jvmRoute)) {
						sessionId = sessionId.substring(0,
								sessionId.length() - this.jvmRoute.length());
					}
					matchingCookieValues.add(sessionId);
				}
			}
		}
		return matchingCookieValues;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.session.web.http.CookieWriter#writeCookieValue(org.
	 * springframework.session.web.http.CookieWriter.CookieValue)
	 */
	public void writeCookieValue(CookieValue cookieValue) {
		HttpServletRequest request = cookieValue.getRequest();
		HttpServletResponse response = cookieValue.getResponse();

		String requestedCookieValue = cookieValue.getCookieValue();
		String actualCookieValue = this.jvmRoute == null ? requestedCookieValue
				: requestedCookieValue + this.jvmRoute;

		Cookie sessionCookie = new Cookie(this.cookieName, this.useBase64Encoding
				? base64Encode(actualCookieValue) : actualCookieValue);
		sessionCookie.setSecure(isSecureCookie(request));
		sessionCookie.setPath(getCookiePath(request));
		String domainName = getDomainName(request);
		if (domainName != null) {
			sessionCookie.setDomain(domainName);
		}

		if (this.useHttpOnlyCookie) {
			sessionCookie.setHttpOnly(true);
		}

		if ("".equals(requestedCookieValue)) {
			sessionCookie.setMaxAge(0);
		}
		else if (this.rememberMeRequestAttribute != null
				&& request.getAttribute(this.rememberMeRequestAttribute) != null) {
			// the cookie is only written at time of session creation, so we rely on
			// session expiration rather than cookie expiration if remember me is enabled
			sessionCookie.setMaxAge(Integer.MAX_VALUE);
		}
		else {
			sessionCookie.setMaxAge(this.cookieMaxAge);
		}

		response.addCookie(sessionCookie);
	}

	/**
	 * Decode the value using Base64.
	 * @param base64Value the Base64 String to decode
	 * @return the Base64 decoded value
	 * @since 1.2.2
	 */
	private String base64Decode(String base64Value) {
		try {
			byte[] decodedCookieBytes = Base64.decode(base64Value.getBytes());
			return new String(decodedCookieBytes);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Encode the value using Base64.
	 * @param value the String to Base64 encode
	 * @return the Base64 encoded value
	 * @since 1.2.2
	 */
	private String base64Encode(String value) {
		byte[] encodedCookieBytes = Base64.encode(value.getBytes());
		return new String(encodedCookieBytes);
	}

	/**
	 * Sets if a Cookie marked as secure should be used. The default is to use the value
	 * of {@link HttpServletRequest#isSecure()}.
	 *
	 * @param useSecureCookie determines if the cookie should be marked as secure.
	 */
	public void setUseSecureCookie(boolean useSecureCookie) {
		this.useSecureCookie = useSecureCookie;
	}

	/**
	 * Sets if a Cookie marked as HTTP Only should be used. The default is true in Servlet
	 * 3+ environments, else false.
	 *
	 * @param useHttpOnlyCookie determines if the cookie should be marked as HTTP Only.
	 */
	public void setUseHttpOnlyCookie(boolean useHttpOnlyCookie) {
		if (useHttpOnlyCookie && !isServlet3()) {
			throw new IllegalArgumentException(
					"You cannot set useHttpOnlyCookie to true in pre Servlet 3 environment");
		}
		this.useHttpOnlyCookie = useHttpOnlyCookie;
	}

	private boolean isSecureCookie(HttpServletRequest request) {
		if (this.useSecureCookie == null) {
			return request.isSecure();
		}
		return this.useSecureCookie;
	}

	/**
	 * Sets the path of the Cookie. The default is to use the context path from the
	 * {@link HttpServletRequest}.
	 *
	 * @param cookiePath the path of the Cookie. If null, the default of the context path
	 * will be used.
	 */
	public void setCookiePath(String cookiePath) {
		this.cookiePath = cookiePath;
	}

	public void setCookieName(String cookieName) {
		if (cookieName == null) {
			throw new IllegalArgumentException("cookieName cannot be null");
		}
		this.cookieName = cookieName;
	}

	/**
	 * Sets the maxAge property of the Cookie. The default is -1 which signals to delete
	 * the cookie when the browser is closed.
	 *
	 * @param cookieMaxAge the maxAge property of the Cookie
	 */
	public void setCookieMaxAge(int cookieMaxAge) {
		this.cookieMaxAge = cookieMaxAge;
	}

	/**
	 * Sets an explicit Domain Name. This allow the domain of "example.com" to be used
	 * when the request comes from www.example.com. This allows for sharing the cookie
	 * across subdomains. The default is to use the current domain.
	 *
	 * @param domainName the name of the domain to use. (i.e. "example.com")
	 * @throws IllegalStateException if the domainNamePattern is also set
	 */
	public void setDomainName(String domainName) {
		if (this.domainNamePattern != null) {
			throw new IllegalStateException(
					"Cannot set both domainName and domainNamePattern");
		}
		this.domainName = domainName;
	}

	/**
	 * <p>
	 * Sets a case insensitive pattern used to extract the domain name from the
	 * {@link HttpServletRequest#getServerName()}. The pattern should provide a single
	 * grouping that defines what the value is that should be matched. User's should be
	 * careful not to output malicious characters like new lines to prevent from things
	 * like <a href= "https://www.owasp.org/index.php/HTTP_Response_Splitting">HTTP
	 * Response Splitting</a>.
	 * </p>
	 *
	 * <p>
	 * If the pattern does not match, then no domain will be set. This is useful to ensure
	 * the domain is not set during development when localhost might be used.
	 * </p>
	 * <p>
	 * An example value might be "^.+?\\.(\\w+\\.[a-z]+)$". For the given input, it would
	 * provide the following explicit domain (null means no domain name is set):
	 * </p>
	 *
	 * <ul>
	 * <li>example.com - null</li>
	 * <li>child.sub.example.com - example.com</li>
	 * <li>localhost - null</li>
	 * <li>127.0.1.1 - null</li>
	 * </ul>
	 *
	 * @param domainNamePattern the case insensitive pattern to extract the domain name
	 * with
	 * @throws IllegalStateException if the domainName is also set
	 */
	public void setDomainNamePattern(String domainNamePattern) {
		if (this.domainName != null) {
			throw new IllegalStateException(
					"Cannot set both domainName and domainNamePattern");
		}
		this.domainNamePattern = Pattern.compile(domainNamePattern,
				Pattern.CASE_INSENSITIVE);
	}

	/**
	 * <p>
	 * Used to identify which JVM to route to for session affinity. With some
	 * implementations (i.e. Redis) this provides no performance benefit. However, this
	 * can help with tracing logs of a particular user. This will ensure that the value of
	 * the cookie is formatted as
	 * </p>
	 * <code>
	 * sessionId + "." jvmRoute
	 * </code>
	 * <p>
	 * To use set a custom route on each JVM instance and setup a frontend proxy to
	 * forward all requests to the JVM based on the route.
	 * </p>
	 *
	 * @param jvmRoute the JVM Route to use (i.e. "node01jvmA", "n01ja", etc)
	 */
	public void setJvmRoute(String jvmRoute) {
		this.jvmRoute = "." + jvmRoute;
	}

	/**
	 * Set if the Base64 encoding of cookie value should be used. This is valuable in
	 * order to support <a href="https://tools.ietf.org/html/rfc6265">RFC 6265</a> which
	 * recommends using Base 64 encoding to the cookie value.
	 *
	 * @param useBase64Encoding the flag to indicate whether to use Base64 encoding
	 */
	public void setUseBase64Encoding(boolean useBase64Encoding) {
		this.useBase64Encoding = useBase64Encoding;
	}

	/**
	 * Set the request attribute name that indicates remember-me login. If specified, the
	 * cookie will be written as Integer.MAX_VALUE.
	 * @param rememberMeRequestAttribute the remember-me request attribute name
	 * @since 1.3.0
	 */
	public void setRememberMeRequestAttribute(String rememberMeRequestAttribute) {
		if (rememberMeRequestAttribute == null) {
			throw new IllegalArgumentException(
					"rememberMeRequestAttribute cannot be null");
		}
		this.rememberMeRequestAttribute = rememberMeRequestAttribute;
	}

	private String getDomainName(HttpServletRequest request) {
		if (this.domainName != null) {
			return this.domainName;
		}
		if (this.domainNamePattern != null) {
			Matcher matcher = this.domainNamePattern.matcher(request.getServerName());
			if (matcher.matches()) {
				return matcher.group(1);
			}
		}
		return null;
	}

	private String getCookiePath(HttpServletRequest request) {
		if (this.cookiePath == null) {
			return request.getContextPath() + "/";
		}
		return this.cookiePath;
	}

	/**
	 * Returns true if the Servlet 3 APIs are detected.
	 *
	 * @return whether the Servlet 3 APIs are detected
	 */
	private boolean isServlet3() {
		try {
			ServletRequest.class.getMethod("startAsync");
			return true;
		}
		catch (NoSuchMethodException e) {
		}
		return false;
	}

}
