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

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Allows for easily ensuring that a request is only invoked once per request. This is a
 * simplified version of spring-web's OncePerRequestFilter and copied to reduce the foot
 * print required to use the session support.
 *
 * @author Rob Winch
 * @since 1.0
 */
abstract class OncePerRequestFilter implements Filter {
	/**
	 * Suffix that gets appended to the filter name for the "already filtered" request
	 * attribute.
	 */
	public static final String ALREADY_FILTERED_SUFFIX = ".FILTERED";

	private String alreadyFilteredAttributeName = getClass().getName()
			.concat(ALREADY_FILTERED_SUFFIX);

	/**
	 * This {@code doFilter} implementation stores a request attribute for
	 * "already filtered", proceeding without filtering again if the attribute is already
	 * there.
	 * @param request the request
	 * @param response the response
	 * @param filterChain the filter chain
	 * @throws ServletException if request is not HTTP request
	 * @throws IOException in case of I/O operation exception
	 */
	public final void doFilter(ServletRequest request, ServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		if (!(request instanceof HttpServletRequest)
				|| !(response instanceof HttpServletResponse)) {
			throw new ServletException(
					"OncePerRequestFilter just supports HTTP requests");
		}
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		boolean hasAlreadyFilteredAttribute = request
				.getAttribute(this.alreadyFilteredAttributeName) != null;

		if (hasAlreadyFilteredAttribute) {

			// Proceed without invoking this filter...
			filterChain.doFilter(request, response);
		}
		else {
			// Do invoke this filter...
			request.setAttribute(this.alreadyFilteredAttributeName, Boolean.TRUE);
			try {
				doFilterInternal(httpRequest, httpResponse, filterChain);
			}
			finally {
				// Remove the "already filtered" request attribute for this request.
				request.removeAttribute(this.alreadyFilteredAttributeName);
			}
		}
	}

	/**
	 * Same contract as for {@code doFilter}, but guaranteed to be just invoked once per
	 * request within a single request thread.
	 * <p>
	 * Provides HttpServletRequest and HttpServletResponse arguments instead of the
	 * default ServletRequest and ServletResponse ones.
	 *
	 * @param request the request
	 * @param response the response
	 * @param filterChain the FilterChain
	 * @throws ServletException thrown when a non-I/O exception has occurred
	 * @throws IOException thrown when an I/O exception of some sort has occurred
	 * @see Filter#doFilter
	 */
	protected abstract void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException;

	public void init(FilterConfig config) {
	}

	public void destroy() {
	}
}
