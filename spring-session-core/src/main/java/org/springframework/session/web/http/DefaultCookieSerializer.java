/*
 * Copyright 2014-2018 the original author or authors.
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default implementation of {@link CookieSerializer}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @author Eddú Meléndez
 * @since 1.1
 */
public class DefaultCookieSerializer implements CookieSerializer {

	private static final Log logger = LogFactory.getLog(DefaultCookieSerializer.class);

	private static final BitSet domainValid = new BitSet(128);

	static {
		for (char c = '0'; c <= '9'; c++) {
			domainValid.set(c);
		}
		for (char c = 'a'; c <= 'z'; c++) {
			domainValid.set(c);
		}
		for (char c = 'A'; c <= 'Z'; c++) {
			domainValid.set(c);
		}
		domainValid.set('.');
		domainValid.set('-');
	}

	private String cookieName = "SESSION";

	private Boolean useSecureCookie;

	private boolean useHttpOnlyCookie = true;

	private String cookiePath;

	private Integer cookieMaxAge;

	private String domainName;

	private Pattern domainNamePattern;

	private String jvmRoute;

	private boolean useBase64Encoding = true;

	private String rememberMeRequestAttribute;

	private String sameSite = "Lax";

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.session.web.http.CookieSerializer#readCookieValues(javax.
	 * servlet.http.HttpServletRequest)
	 */
	@Override
	public List<String> readCookieValues(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		List<String> matchingCookieValues = new ArrayList<>();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (this.cookieName.equals(cookie.getName())) {
					String sessionId = (this.useBase64Encoding
							? base64Decode(cookie.getValue())
							: cookie.getValue());
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
	@Override
	public void writeCookieValue(CookieValue cookieValue) {
		HttpServletRequest request = cookieValue.getRequest();
		HttpServletResponse response = cookieValue.getResponse();

		StringBuilder sb = new StringBuilder();
		sb.append(this.cookieName).append('=');
		String value = getValue(cookieValue);
		if (value != null && value.length() > 0) {
			validateValue(value);
			sb.append(value);
		}
		int maxAge = getMaxAge(cookieValue);
		if (maxAge > -1) {
			sb.append("; Max-Age=").append(cookieValue.getCookieMaxAge());
			OffsetDateTime expires = (maxAge != 0)
					? OffsetDateTime.now().plusSeconds(maxAge)
					: Instant.EPOCH.atOffset(ZoneOffset.UTC);
			sb.append("; Expires=")
					.append(expires.format(DateTimeFormatter.RFC_1123_DATE_TIME));
		}
		String domain = getDomainName(request);
		if (domain != null && domain.length() > 0) {
			validateDomain(domain);
			sb.append("; Domain=").append(domain);
		}
		String path = getCookiePath(request);
		if (path != null && path.length() > 0) {
			validatePath(path);
			sb.append("; Path=").append(path);
		}
		if (isSecureCookie(request)) {
			sb.append("; Secure");
		}
		if (this.useHttpOnlyCookie) {
			sb.append("; HttpOnly");
		}
		if (this.sameSite != null) {
			sb.append("; SameSite=").append(this.sameSite);
		}

		response.addHeader("Set-Cookie", sb.toString());
	}

	/**
	 * Decode the value using Base64.
	 * @param base64Value the Base64 String to decode
	 * @return the Base64 decoded value
	 * @since 1.2.2
	 */
	private String base64Decode(String base64Value) {
		try {
			byte[] decodedCookieBytes = Base64.getDecoder().decode(base64Value);
			return new String(decodedCookieBytes);
		}
		catch (Exception ex) {
			logger.debug("Unable to Base64 decode value: " + base64Value);
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
		byte[] encodedCookieBytes = Base64.getEncoder().encode(value.getBytes());
		return new String(encodedCookieBytes);
	}

	private String getValue(CookieValue cookieValue) {
		String requestedCookieValue = cookieValue.getCookieValue();
		String actualCookieValue = requestedCookieValue;
		if (this.jvmRoute != null) {
			actualCookieValue = requestedCookieValue + this.jvmRoute;
		}
		if (this.useBase64Encoding) {
			actualCookieValue = base64Encode(actualCookieValue);
		}
		return actualCookieValue;
	}

	private void validateValue(String value) {
		int start = 0;
		int end = value.length();
		if ((end > 1) && (value.charAt(0) == '"') && (value.charAt(end - 1) == '"')) {
			start = 1;
			end--;
		}
		char[] chars = value.toCharArray();
		for (int i = start; i < end; i++) {
			char c = chars[i];
			if (c < 0x21 || c == 0x22 || c == 0x2c || c == 0x3b || c == 0x5c
					|| c == 0x7f) {
				throw new IllegalArgumentException(
						"Invalid character in cookie value: " + Integer.toString(c));
			}
		}
	}

	private int getMaxAge(CookieValue cookieValue) {
		int maxAge = cookieValue.getCookieMaxAge();
		if (maxAge < 0) {
			if (this.rememberMeRequestAttribute != null && cookieValue.getRequest()
					.getAttribute(this.rememberMeRequestAttribute) != null) {
				// the cookie is only written at time of session creation, so we rely on
				// session expiration rather than cookie expiration if remember me is
				// enabled
				cookieValue.setCookieMaxAge(Integer.MAX_VALUE);
			}
			else if (this.cookieMaxAge != null) {
				cookieValue.setCookieMaxAge(this.cookieMaxAge);
			}
		}
		return cookieValue.getCookieMaxAge();
	}

	private void validateDomain(String domain) {
		int i = 0;
		int cur = -1;
		int prev;
		char[] chars = domain.toCharArray();
		while (i < chars.length) {
			prev = cur;
			cur = chars[i];
			if (!domainValid.get(cur)
					|| ((prev == '.' || prev == -1) && (cur == '.' || cur == '-'))
					|| (prev == '-' && cur == '.')) {
				throw new IllegalArgumentException("Invalid cookie domain: " + domain);
			}
			i++;
		}
		if (cur == '.' || cur == '-') {
			throw new IllegalArgumentException("Invalid cookie domain: " + domain);
		}
	}

	private void validatePath(String path) {
		for (char ch : path.toCharArray()) {
			if (ch < 0x20 || ch > 0x7E || ch == ';') {
				throw new IllegalArgumentException("Invalid cookie path: " + path);
			}
		}
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
	 * Sets if a Cookie marked as HTTP Only should be used. The default is true.
	 *
	 * @param useHttpOnlyCookie determines if the cookie should be marked as HTTP Only.
	 */
	public void setUseHttpOnlyCookie(boolean useHttpOnlyCookie) {
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
	 * Sets the maxAge property of the Cookie. The default is to delete the cookie when
	 * the browser is closed.
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

	/**
	 * Set the value for the {@code SameSite} cookie directive. The default value is
	 * {@code Lax}.
	 * @param sameSite the SameSite directive value
	 * @since 2.1.0
	 */
	public void setSameSite(String sameSite) {
		this.sameSite = sameSite;
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

}
