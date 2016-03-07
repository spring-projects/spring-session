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

/**
 * Allows customizing the {@link HttpServletRequest} and/or the
 * {@link HttpServletResponse}.
 *
 * @author Rob Winch
 * @since 1.0
 */
public interface RequestResponsePostProcessor {

	/**
	 * Allows customizing the {@link HttpServletRequest}.
	 *
	 * @param request the original {@link HttpServletRequest}. Cannot be null.
	 * @param response the original {@link HttpServletResponse}. This is NOT the result of
	 * {@link #wrapResponse(HttpServletRequest, HttpServletResponse)} Cannot be null. .
	 * @return a non-null {@link HttpServletRequest}
	 */
	HttpServletRequest wrapRequest(HttpServletRequest request,
			HttpServletResponse response);

	/**
	 * Allows customizing the {@link HttpServletResponse}.
	 *
	 * @param request the original {@link HttpServletRequest}. This is NOT the result of
	 * {@link #wrapRequest(HttpServletRequest, HttpServletResponse)}. Cannot be null.
	 * @param response the original {@link HttpServletResponse}. Cannot be null.
	 * @return a non-null {@link HttpServletResponse}
	 */
	HttpServletResponse wrapResponse(HttpServletRequest request,
			HttpServletResponse response);
}
