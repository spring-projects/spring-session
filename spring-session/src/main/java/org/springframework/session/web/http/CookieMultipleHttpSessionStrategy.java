/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.session.web.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link HttpSessionStrategy} that uses cookies to save multiple user
 * sessions. One cookie stores all valid sessions seperated by a delimiter and
 * the other cookie stores the current session id. Cookie names can be specified
 * by {@link CookieMultipleHttpSessionStrategy#setSessionsCookieName(String)}
 * and {@link CookieMultipleHttpSessionStrategy#setCookieName(String)}. Default
 * values are "SESSIONS" and "CURRENT_SESSION".
 * 
 * When a session is created, the HTTP response will have a cookie with the
 * specified cookie name and the value for all valid session ids and another
 * cookie with specified cookie name and value for current session id. Both
 * cookie will be marked as session cookies, use the context path for the path
 * of the cookies, marked as HTTPOnly, and if
 * {@link javax.servlet.http.HttpServletRequest#isSecure()} returns true, the
 * cookies will be marked as secure. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSIONS=g11d4fa4-7eec-10d0-a765-00a1c91e6be3~f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Path=/context-root; Secure; HttpOnly
 * Set-Cookie: CURRENT_SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Path=/context-root; Secure; HttpOnly
 * </pre>
 *
 * The client should now include the sessions cookie and the current session
 * cookie in each request by specifying the same cookie in their request. For
 * example:
 *
 * <pre>
 * GET /messages/ HTTP/1.1
 * Host: example.com
 * Cookie: SESSIONS=g11d4fa4-7eec-10d0-a765-00a1c91e6be3~f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * Cookie: CURRENT_SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * </pre>
 *
 * When the session is invalidated, the server will send an HTTP response that
 * expires the current session cookie and removes the current session id from
 * the sessions cookies, if no more sessions are left then sessions cookie is
 * also expired. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: CURRENT_SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Expires=Thur, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly
 * </pre>
 *
 * @since 1.0
 * @author Adeel Qureshi
 */
public final class CookieMultipleHttpSessionStrategy implements HttpSessionStrategy {
	private String sessionsCookieName = "SESSIONS";
	private String currentCookieName = "CURRENT_SESSION";

	private final static String DELIM = "~";

	@Override
	public String getRequestedSessionId(HttpServletRequest request) {
		Cookie sessions = getCookie(request, sessionsCookieName);
		Cookie current = getCookie(request, currentCookieName);
		if (sessions != null && current != null) {
			return containsId(sessions.getValue(), current.getValue()) ? current.getValue() : null;
		}
		return null;
	}

	@Override
	public void onNewSession(Session session, HttpServletRequest request, HttpServletResponse response) {
		Cookie sessions = getCookie(request, sessionsCookieName);
		System.out.println("previous sessions found " + sessions);
		String ids = sessions == null ? session.getId() : sessions.getValue() + DELIM + session.getId();
		System.out.println("final ids " + ids);
		setCookie(request, response, sessionsCookieName, ids);
		setCookie(request, response, currentCookieName, session.getId());

		// TODO set the domain?
	}

	@Override
	public void onInvalidateSession(HttpServletRequest request, HttpServletResponse response) {
		Cookie sessions = getCookie(request, sessionsCookieName);
		Cookie current = getCookie(request, currentCookieName);
		String ids = "";
		if (sessions != null && current != null) {
			ids = removeId(sessions.getValue(), current.getValue());
		}
		setCookie(request, response, sessionsCookieName, ids);
		setCookie(request, response, currentCookieName, "");
	}

	/**
	 * Sets the name of sessions cookie to be used. This is where the ids of all
	 * valid sessions are stored.
	 * 
	 * @param cookieName
	 */
	public void setSessionsCookieName(String cookieName) {
		Assert.notNull(cookieName, "cookieName cannot be null");
		this.sessionsCookieName = cookieName;
	}

	/**
	 * Sets the name of current session cookie to be used. This is where the id
	 * of the current valid session is stored.
	 * 
	 * @param cookieName
	 */
	public void setCurrentCookieName(String cookieName) {
		Assert.notNull(cookieName, "cookieName cannot be null");
		this.currentCookieName = cookieName;
	}

	/**
	 * Retrieve the first cookie with the given name. Note that multiple cookies
	 * can have the same name but different paths or domains.
	 * 
	 * @param request
	 *            current servlet request
	 * @param name
	 *            cookie name
	 * @return the first cookie with the given name, or {@code null} if none is
	 *         found
	 */
	private static Cookie getCookie(HttpServletRequest request, String name) {
		Assert.notNull(request, "Request must not be null");
		Cookie cookies[] = request.getCookies();
		Cookie result = null;
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (name.equals(cookie.getName())) {
					if (cookiePath(request).equals(cookie.getPath())) {
						return cookie;
					}
					result = cookie;
				}
			}
		}
		return result;
	}

	/**
	 * Sets the value of a cookie with given name to specified value. If empty
	 * string is specified as value then cookie is expired. If the cookie
	 * already exists its value is updated to given value.
	 * 
	 * @param request
	 * @param response
	 * @param name
	 * @param value
	 */
	private static void setCookie(HttpServletRequest request, HttpServletResponse response, String name, String value) {
		Assert.notNull(request, "Request must not be null");
		Assert.notNull(response, "Response must not be null");
		Cookie cookie = getCookie(request, name);
		if (cookie == null) {
			System.out.println("creating new cookie with name " + name + " to value " + value);
			cookie = new Cookie(name, value);
		} else {
			System.out.println("updating cookie with name " + name + " to value " + value);
			cookie.setValue(value);
		}
		if ("".equals(value)) {
			System.out.println("expiring cookie with name " + name + " to value " + value);
			cookie.setMaxAge(0);
		}
		cookie.setHttpOnly(true);
		cookie.setSecure(request.isSecure());
		cookie.setPath(cookiePath(request));
		response.addCookie(cookie);
	}

	private static String cookiePath(HttpServletRequest request) {
		return request.getContextPath() + "/";
	}

	/**
	 * Looks for given session id in the list of existing session ids
	 * 
	 * @param ids
	 * @param id
	 * @return true if given session id exists in existing session ids list
	 *         otherwise false
	 */
	private static boolean containsId(String ids, String id) {
		boolean valid = false;
		if (StringUtils.isEmpty(ids)) {
			return valid;
		}
		return Arrays.asList(ids.split(DELIM)).indexOf(id) > 0;
	}

	/**
	 * Removes the given session id from existing list of session ids
	 * 
	 * @param ids
	 * @param id
	 * @return list of session ids after removing the given session id
	 */
	private static String removeId(String ids, String id) {
		if (StringUtils.isEmpty(ids) || StringUtils.isEmpty(id)) {
			return ids;
		}
		List<String> tokens = Arrays.asList(ids.split(DELIM));
		List<String> removed = new ArrayList<String>(tokens.size());
		for (String token : tokens) {
			if (!id.equals(token)) {
				removed.add(token);
			}
		}
		return StringUtils.collectionToDelimitedString(removed, DELIM);
	}
}