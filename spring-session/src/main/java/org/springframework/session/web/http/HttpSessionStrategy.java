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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.session.Session;

/**
 * A strategy for mapping HTTP request and responses to a {@link Session}.
 *
 * @author Rob Winch
 * @since 1.0
 */
public interface HttpSessionStrategy {

	/**
	 * Obtains the requested session id from the provided
	 * {@link javax.servlet.http.HttpServletRequest}. For example, the session id might
	 * come from a cookie or a request header.
	 *
	 * @param request the {@link javax.servlet.http.HttpServletRequest} to obtain the
	 * session id from. Cannot be null.
	 * @return the {@link javax.servlet.http.HttpServletRequest} to obtain the session id
	 * from.
	 */
	String getRequestedSessionId(HttpServletRequest request);

	/**
	 * This method is invoked when a new session is created and should inform a client
	 * what the new session id is. For example, it might create a new cookie with the
	 * session id in it or set an HTTP response header with the value of the new session
	 * id.
	 *
	 * Some implementations may wish to associate additional information to the
	 * {@link Session} at this time. For example, they may wish to add the IP Address,
	 * browser headers, the username, etc to the
	 * {@link org.springframework.session.Session}.
	 *
	 * @param session the {@link org.springframework.session.Session} that is being sent
	 * to the client. Cannot be null.
	 * @param request the {@link javax.servlet.http.HttpServletRequest} that create the
	 * new {@link org.springframework.session.Session} Cannot be null.
	 * @param response the {@link javax.servlet.http.HttpServletResponse} that is
	 * associated with the {@link javax.servlet.http.HttpServletRequest} that created the
	 * new {@link org.springframework.session.Session} Cannot be null.
	 */
	void onNewSession(Session session, HttpServletRequest request,
			HttpServletResponse response);

	/**
	 * This method is invoked when a session is invalidated and should inform a client
	 * that the session id is no longer valid. For example, it might remove a cookie with
	 * the session id in it or set an HTTP response header with an empty value indicating
	 * to the client to no longer submit that session id.
	 *
	 * @param request the {@link javax.servlet.http.HttpServletRequest} that invalidated
	 * the {@link org.springframework.session.Session} Cannot be null.
	 * @param response the {@link javax.servlet.http.HttpServletResponse} that is
	 * associated with the {@link javax.servlet.http.HttpServletRequest} that invalidated
	 * the {@link org.springframework.session.Session} Cannot be null.
	 */
	void onInvalidateSession(HttpServletRequest request, HttpServletResponse response);
}
