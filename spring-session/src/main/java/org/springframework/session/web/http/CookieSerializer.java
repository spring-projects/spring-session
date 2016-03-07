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

import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Strategy for reading and writing a cookie value to the {@link HttpServletResponse}.
 *
 * @author Rob Winch
 * @since 1.1
 */
public interface CookieSerializer {

	/**
	 * Writes a given {@link CookieValue} to the provided {@link HttpServletResponse}.
	 *
	 * @param cookieValue the {@link CookieValue} to write to
	 * {@link CookieValue#getResponse()}. Cannot be null.
	 */
	void writeCookieValue(CookieValue cookieValue);

	/**
	 * Reads all the matching cookies from the {@link HttpServletRequest}. The result is a
	 * List since there can be multiple {@link Cookie} in a single request with a matching
	 * name. For example, one Cookie may have a path of / and another of /context, but the
	 * path is not transmitted in the request.
	 *
	 * @param request the {@link HttpServletRequest} to read the cookie from. Cannot be
	 * null.
	 * @return the values of all the matching cookies
	 */
	List<String> readCookieValues(HttpServletRequest request);

	/**
	 * Contains the information necessary to write a value to the
	 * {@link HttpServletResponse}.
	 *
	 * @author Rob Winch
	 * @since 1.1
	 */
	class CookieValue {
		private final HttpServletRequest request;
		private final HttpServletResponse response;
		private final String cookieValue;

		/**
		 * Creates a new instance.
		 *
		 * @param request the {@link HttpServletRequest} to use. Useful for determining
		 * the context in which the cookie is set. Cannot be null.
		 * @param response the {@link HttpServletResponse} to use.
		 * @param cookieValue the value of the cookie to be written. This value may be
		 * modified by the {@link CookieSerializer} when writing to the actual cookie so
		 * long as the original value is returned when the cookie is read.
		 */
		public CookieValue(HttpServletRequest request, HttpServletResponse response,
				String cookieValue) {
			this.request = request;
			this.response = response;
			this.cookieValue = cookieValue;
		}

		/**
		 * Gets the request to use.
		 * @return the request to use. Cannot be null.
		 */
		public HttpServletRequest getRequest() {
			return this.request;
		}

		/**
		 * Gets the response to write to.
		 * @return the response to write to. Cannot be null.
		 */
		public HttpServletResponse getResponse() {
			return this.response;
		}

		/**
		 * The value to be written. This value may be modified by the
		 * {@link CookieSerializer} before written to the cookie. However, the value must
		 * be the same as the original when it is read back in.
		 *
		 * @return the value to be written
		 */
		public String getCookieValue() {
			return this.cookieValue;
		}
	}
}
