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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Allows managing a mapping of alias to the session id for having multiple active
 * sessions at the same time.
 *
 * @author Rob Winch
 * @since 1.0
 *
 */
public interface HttpSessionManager {

	/**
	 * Gets the current session's alias from the {@link HttpServletRequest}.
	 *
	 * @param request the {@link HttpServletRequest} to obtain the current session's alias
	 * from.
	 * @return the current sessions' alias. Cannot be null.
	 */
	String getCurrentSessionAlias(HttpServletRequest request);

	/**
	 * Gets a mapping of the session alias to the session id from the
	 * {@link HttpServletRequest}.
	 *
	 * @param request the {@link HttpServletRequest} to obtain the mapping from. Cannot be
	 * null.
	 * @return a mapping of the session alias to the session id from the
	 * {@link HttpServletRequest}. Cannot be null.
	 */
	Map<String, String> getSessionIds(HttpServletRequest request);

	/**
	 * Provides the ability to encode the URL for a given session alias.
	 *
	 * @param url the url to encode.
	 * @param sessionAlias the session alias to encode.
	 * @return the encoded URL
	 */
	String encodeURL(String url, String sessionAlias);

	/**
	 * Gets a new and unique Session alias. Typically this will be called to pass into
	 * {@code HttpSessionManager#encodeURL(java.lang.String)}. For example:
	 *
	 * <code>
	 * String newAlias = httpSessionManager.getNewSessionAlias(request);
	 * String addAccountUrl = httpSessionManager.encodeURL("./", newAlias);
	 * </code>
	 *
	 * @param request the {@link HttpServletRequest} to get a new alias from
	 * @return Gets a new and unique Session alias.
	 */
	String getNewSessionAlias(HttpServletRequest request);
}
