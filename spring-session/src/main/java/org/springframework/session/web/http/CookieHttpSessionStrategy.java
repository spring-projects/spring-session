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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.session.Session;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.util.Assert;

/**
 * A {@link HttpSessionStrategy} that uses a cookie to obtain the session from.
 * Specifically, this implementation will allow specifying a cookie name using
 * {@link CookieHttpSessionStrategy#setCookieName(String)}. The default is "SESSION".
 *
 * When a session is created, the HTTP response will have a cookie with the specified
 * cookie name and the value of the session id. The cookie will be marked as a session
 * cookie, use the context path for the path of the cookie, marked as HTTPOnly, and if
 * {@link javax.servlet.http.HttpServletRequest#isSecure()} returns true, the cookie will
 * be marked as secure. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Path=/context-root; Secure; HttpOnly
 * </pre>
 *
 * The client should now include the session in each request by specifying the same cookie
 * in their request. For example:
 *
 * <pre>
 * GET /messages/ HTTP/1.1
 * Host: example.com
 * Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * </pre>
 *
 * When the session is invalidated, the server will send an HTTP response that expires the
 * cookie. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Expires=Thur, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly
 * </pre>
 *
 * <h2>Supporting Multiple Simultaneous Sessions</h2>
 *
 * <p>
 * By default multiple sessions are also supported. Once a session is established with the
 * browser, another session can be initiated by specifying a unique value for the
 * {@link #setSessionAliasParamName(String)}. For example, a request to:
 * </p>
 *
 * <pre>
 * GET /messages/?_s=1416195761178 HTTP/1.1
 * Host: example.com
 * Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * </pre>
 *
 * Will result in the following response:
 *
 * <pre>
 *  HTTP/1.1 200 OK
 * Set-Cookie: SESSION="0 f81d4fae-7dec-11d0-a765-00a0c91e6bf6 1416195761178 8a929cde-2218-4557-8d4e-82a79a37876d"; Expires=Thur, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly
 * </pre>
 *
 * <p>
 * To use the original session a request without the HTTP parameter u can be made. To use
 * the new session, a request with the HTTP parameter _s=1416195761178 can be used. By
 * default URLs will be rewritten to include the currently selected session.
 * </p>
 *
 * <h2>Selecting Sessions</h2>
 *
 * <p>
 * Sessions can be managed by using the HttpSessionManager and SessionRepository. If you
 * are not using Spring in the rest of your application you can obtain a reference from
 * the HttpServletRequest attributes. An example is provided below:
 * </p>
 *
 * <code>
 *      HttpSessionManager sessionManager =
 *              (HttpSessionManager) req.getAttribute(HttpSessionManager.class.getName());
 *      SessionRepository&lt;Session&gt; repo =
 *              (SessionRepository&lt;Session&gt;) req.getAttribute(SessionRepository.class.getName());
 *
 *      String currentSessionAlias = sessionManager.getCurrentSessionAlias(req);
 *      Map&lt;String, String&gt; sessionIds = sessionManager.getSessionIds(req);
 *      String newSessionAlias = String.valueOf(System.currentTimeMillis());
 *
 *      String contextPath = req.getContextPath();
 *      List&lt;Account&gt; accounts = new ArrayList&lt;&gt;();
 *      Account currentAccount = null; for(Map.Entry&lt;String, String&gt; entry :
 * sessionIds.entrySet()) { String alias = entry.getKey(); String sessionId =
 * entry.getValue();
 * </code>
 *
 * Session session = repo.getSession(sessionId); if(session == null) { continue; }
 *
 * String username = session.getAttribute("username"); if(username == null) {
 * newSessionAlias = alias; continue; }
 *
 * String logoutUrl = sessionManager.encodeURL("./logout", alias); String switchAccountUrl
 * = sessionManager.encodeURL("./", alias); Account account = new Account(username,
 * logoutUrl, switchAccountUrl); if(currentSessionAlias.equals(alias)) { currentAccount =
 * account; } else { accounts.add(account); } }
 *
 * req.setAttribute("currentAccount", currentAccount); req.setAttribute("addAccountUrl",
 * sessionManager.encodeURL(contextPath, newSessionAlias)); req.setAttribute("accounts",
 * accounts); }
 *
 *
 * @author Rob Winch
 * @since 1.0
 */
public final class CookieHttpSessionStrategy
		implements MultiHttpSessionStrategy, HttpSessionManager {
	/**
	 * The default delimiter for both serialization and deserialization.
	 */
	private static final String DEFAULT_DELIMITER = " ";

	private static final String SESSION_IDS_WRITTEN_ATTR = CookieHttpSessionStrategy.class
			.getName().concat(".SESSIONS_WRITTEN_ATTR");

	static final String DEFAULT_ALIAS = "0";

	static final String DEFAULT_SESSION_ALIAS_PARAM_NAME = "_s";

	private static final Pattern ALIAS_PATTERN = Pattern.compile("^[\\w-]{1,50}$");

	private String sessionParam = DEFAULT_SESSION_ALIAS_PARAM_NAME;

	private CookieSerializer cookieSerializer = new DefaultCookieSerializer();

	/**
	 * The delimiter between a session alias and a session id when reading a cookie value.
	 * The default value is " ".
	 */
	private String deserializationDelimiter = DEFAULT_DELIMITER;

	/**
	 * The delimiter between a session alias and a session id when writing a cookie value.
	 * The default is " ".
	 */
	private String serializationDelimiter = DEFAULT_DELIMITER;

	public String getRequestedSessionId(HttpServletRequest request) {
		Map<String, String> sessionIds = getSessionIds(request);
		String sessionAlias = getCurrentSessionAlias(request);
		return sessionIds.get(sessionAlias);
	}

	public String getCurrentSessionAlias(HttpServletRequest request) {
		if (this.sessionParam == null) {
			return DEFAULT_ALIAS;
		}
		String u = request.getParameter(this.sessionParam);
		if (u == null) {
			return DEFAULT_ALIAS;
		}
		if (!ALIAS_PATTERN.matcher(u).matches()) {
			return DEFAULT_ALIAS;
		}
		return u;
	}

	public String getNewSessionAlias(HttpServletRequest request) {
		Set<String> sessionAliases = getSessionIds(request).keySet();
		if (sessionAliases.isEmpty()) {
			return DEFAULT_ALIAS;
		}
		long lastAlias = Long.decode(DEFAULT_ALIAS);
		for (String alias : sessionAliases) {
			long selectedAlias = safeParse(alias);
			if (selectedAlias > lastAlias) {
				lastAlias = selectedAlias;
			}
		}
		return Long.toHexString(lastAlias + 1);
	}

	private long safeParse(String hex) {
		try {
			return Long.decode("0x" + hex);
		}
		catch (NumberFormatException notNumber) {
			return 0;
		}
	}

	public void onNewSession(Session session, HttpServletRequest request,
			HttpServletResponse response) {
		Set<String> sessionIdsWritten = getSessionIdsWritten(request);
		if (sessionIdsWritten.contains(session.getId())) {
			return;
		}
		sessionIdsWritten.add(session.getId());

		Map<String, String> sessionIds = getSessionIds(request);
		String sessionAlias = getCurrentSessionAlias(request);
		sessionIds.put(sessionAlias, session.getId());

		String cookieValue = createSessionCookieValue(sessionIds);
		this.cookieSerializer
				.writeCookieValue(new CookieValue(request, response, cookieValue));
	}

	@SuppressWarnings("unchecked")
	private Set<String> getSessionIdsWritten(HttpServletRequest request) {
		Set<String> sessionsWritten = (Set<String>) request
				.getAttribute(SESSION_IDS_WRITTEN_ATTR);
		if (sessionsWritten == null) {
			sessionsWritten = new HashSet<String>();
			request.setAttribute(SESSION_IDS_WRITTEN_ATTR, sessionsWritten);
		}
		return sessionsWritten;
	}

	private String createSessionCookieValue(Map<String, String> sessionIds) {
		if (sessionIds.isEmpty()) {
			return "";
		}
		if (sessionIds.size() == 1 && sessionIds.keySet().contains(DEFAULT_ALIAS)) {
			return sessionIds.values().iterator().next();
		}

		StringBuffer buffer = new StringBuffer();
		for (Map.Entry<String, String> entry : sessionIds.entrySet()) {
			String alias = entry.getKey();
			String id = entry.getValue();

			buffer.append(alias);
			buffer.append(this.serializationDelimiter);
			buffer.append(id);
			buffer.append(this.serializationDelimiter);
		}
		buffer.deleteCharAt(buffer.length() - 1);
		return buffer.toString();
	}

	public void onInvalidateSession(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> sessionIds = getSessionIds(request);
		String requestedAlias = getCurrentSessionAlias(request);
		sessionIds.remove(requestedAlias);

		String cookieValue = createSessionCookieValue(sessionIds);
		this.cookieSerializer
				.writeCookieValue(new CookieValue(request, response, cookieValue));
	}

	/**
	 * Sets the name of the HTTP parameter that is used to specify the session alias. If
	 * the value is null, then only a single session is supported per browser.
	 *
	 * @param sessionAliasParamName the name of the HTTP parameter used to specify the
	 * session alias. If null, then ony a single session is supported per browser.
	 */
	public void setSessionAliasParamName(String sessionAliasParamName) {
		this.sessionParam = sessionAliasParamName;
	}

	/**
	 * Sets the {@link CookieSerializer} to be used.
	 *
	 * @param cookieSerializer the cookieSerializer to set. Cannot be null.
	 */
	public void setCookieSerializer(CookieSerializer cookieSerializer) {
		Assert.notNull(cookieSerializer, "cookieSerializer cannot be null");
		this.cookieSerializer = cookieSerializer;
	}

	/**
	 * Sets the name of the cookie to be used.
	 * @param cookieName the name of the cookie to be used
	 * @deprecated use {@link #setCookieSerializer(CookieSerializer)}
	 */
	@Deprecated
	public void setCookieName(String cookieName) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(cookieName);
		this.cookieSerializer = serializer;
	}

	/**
	 * Sets the delimiter between a session alias and a session id when deserializing a
	 * cookie. The default is " " This is useful when using
	 * <a href="https://tools.ietf.org/html/rfc6265">RFC 6265</a> for writing the cookies
	 * which doesn't allow for spaces in the cookie values.
	 *
	 * @param delimiter the delimiter to set (i.e. "_ " will try a delimeter of either "_"
	 * or " ")
	 */
	public void setDeserializationDelimiter(String delimiter) {
		this.deserializationDelimiter = delimiter;
	}

	/**
	 * Sets the delimiter between a session alias and a session id when deserializing a
	 * cookie. The default is " ". This is useful when using
	 * <a href="https://tools.ietf.org/html/rfc6265">RFC 6265</a> for writing the cookies
	 * which doesn't allow for spaces in the cookie values.
	 *
	 * @param delimiter the delimiter to set (i.e. "_")
	 */
	public void setSerializationDelimiter(String delimiter) {
		this.serializationDelimiter = delimiter;
	}

	public Map<String, String> getSessionIds(HttpServletRequest request) {
		List<String> cookieValues = this.cookieSerializer.readCookieValues(request);
		String sessionCookieValue = cookieValues.isEmpty() ? ""
				: cookieValues.iterator().next();
		Map<String, String> result = new LinkedHashMap<String, String>();
		StringTokenizer tokens = new StringTokenizer(sessionCookieValue,
				this.deserializationDelimiter);
		if (tokens.countTokens() == 1) {
			result.put(DEFAULT_ALIAS, tokens.nextToken());
			return result;
		}
		while (tokens.hasMoreTokens()) {
			String alias = tokens.nextToken();
			if (!tokens.hasMoreTokens()) {
				break;
			}
			String id = tokens.nextToken();
			result.put(alias, id);
		}
		return result;
	}

	public HttpServletRequest wrapRequest(HttpServletRequest request,
			HttpServletResponse response) {
		request.setAttribute(HttpSessionManager.class.getName(), this);
		return request;
	}

	public HttpServletResponse wrapResponse(HttpServletRequest request,
			HttpServletResponse response) {
		return new MultiSessionHttpServletResponse(response, request);
	}

	public String encodeURL(String url, String sessionAlias) {
		String encodedSessionAlias = urlEncode(sessionAlias);
		int queryStart = url.indexOf("?");
		boolean isDefaultAlias = DEFAULT_ALIAS.equals(encodedSessionAlias);
		if (queryStart < 0) {
			return isDefaultAlias ? url
					: url + "?" + this.sessionParam + "=" + encodedSessionAlias;
		}
		String path = url.substring(0, queryStart);
		String query = url.substring(queryStart + 1, url.length());
		String replacement = isDefaultAlias ? "" : "$1" + encodedSessionAlias;
		query = query.replaceFirst("((^|&)" + this.sessionParam + "=)([^&]+)?",
				replacement);
		String sessionParamReplacement = String.format("%s=%s", this.sessionParam,
				encodedSessionAlias);

		if (!isDefaultAlias && !query.contains(sessionParamReplacement)
				&& url.endsWith(query)) {
			// no existing alias
			if (!(query.endsWith("&") || query.length() == 0)) {
				query += "&";
			}
			query += sessionParamReplacement;
		}

		return path + "?" + query;
	}

	private String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * A {@link CookieHttpSessionStrategy} aware {@link HttpServletResponseWrapper}.
	 */
	class MultiSessionHttpServletResponse extends HttpServletResponseWrapper {
		private final HttpServletRequest request;

		MultiSessionHttpServletResponse(HttpServletResponse response,
				HttpServletRequest request) {
			super(response);
			this.request = request;
		}

		private String getCurrentSessionAliasFromUrl(String url) {
			String currentSessionAliasFromUrl = null;
			int queryStart = url.indexOf("?");

			if (queryStart >= 0) {
				String query = url.substring(queryStart + 1);
				Matcher matcher = Pattern
						.compile(String.format("%s=([^&]+)",
								CookieHttpSessionStrategy.this.sessionParam))
						.matcher(query);

				if (matcher.find()) {
					currentSessionAliasFromUrl = matcher.group(1);
				}
			}

			return currentSessionAliasFromUrl;
		}

		@Override
		public String encodeRedirectURL(String url) {
			String encodedUrl = super.encodeRedirectURL(url);
			String currentSessionAliasFromUrl = getCurrentSessionAliasFromUrl(encodedUrl);
			String alias = (currentSessionAliasFromUrl != null)
					? currentSessionAliasFromUrl : getCurrentSessionAlias(this.request);

			return CookieHttpSessionStrategy.this.encodeURL(encodedUrl, alias);
		}

		@Override
		public String encodeURL(String url) {
			String encodedUrl = super.encodeURL(url);
			String currentSessionAliasFromUrl = getCurrentSessionAliasFromUrl(encodedUrl);
			String alias = (currentSessionAliasFromUrl != null)
					? currentSessionAliasFromUrl : getCurrentSessionAlias(this.request);

			return CookieHttpSessionStrategy.this.encodeURL(encodedUrl, alias);
		}
	}

}
