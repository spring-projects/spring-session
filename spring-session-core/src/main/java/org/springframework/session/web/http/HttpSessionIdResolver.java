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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Contract for session id resolution strategies. Allows for session id resolution through
 * the request and for sending the session id or expiring the session through the
 * response.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 2.0.0
 */
public interface HttpSessionIdResolver {

	/**
	 * Resolve the session ids associated with the provided {@link HttpServletRequest}.
	 * For example, the session id might come from a cookie or a request header.
	 * @param request the current request
	 * @return the session ids
	 */
	List<String> resolveSessionIds(HttpServletRequest request);

	/**
	 * Send the given session id to the client. This method is invoked when a new session
	 * is created and should inform a client what the new session id is. For example, it
	 * might create a new cookie with the session id in it or set an HTTP response header
	 * with the value of the new session id.
	 * @param request the current request
	 * @param response the current response
	 * @param sessionId the session id
	 */
	void setSessionId(HttpServletRequest request, HttpServletResponse response,
			String sessionId);

	/**
	 * Instruct the client to end the current session. This method is invoked when a
	 * session is invalidated and should inform a client that the session id is no longer
	 * valid. For example, it might remove a cookie with the session id in it or set an
	 * HTTP response header with an empty value indicating to the client to no longer
	 * submit that session id.
	 * @param request the current request
	 * @param response the current response
	 */
	void expireSession(HttpServletRequest request, HttpServletResponse response);

}
