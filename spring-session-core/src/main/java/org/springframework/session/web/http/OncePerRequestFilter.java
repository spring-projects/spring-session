/*
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.web.http;

import java.io.IOException;

import javax.servlet.DispatcherType;
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

	private String alreadyFilteredAttributeName = getClass().getName().concat(ALREADY_FILTERED_SUFFIX);

	/**
	 * This {@code doFilter} implementation stores a request attribute for "already
	 * filtered", proceeding without filtering again if the attribute is already there.
	 * @param request the request
	 * @param response the response
	 * @param filterChain the filter chain
	 * @throws ServletException if request is not HTTP request
	 * @throws IOException in case of I/O operation exception
	 */
	@Override
	public final void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
			throw new ServletException("OncePerRequestFilter just supports HTTP requests");
		}
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String alreadyFilteredAttributeName = getAlreadyFilteredAttributeName();
		boolean hasAlreadyFilteredAttribute = request.getAttribute(alreadyFilteredAttributeName) != null;

		if (hasAlreadyFilteredAttribute) {
			if (DispatcherType.ERROR.equals(request.getDispatcherType())) {
				doFilterNestedErrorDispatch(httpRequest, httpResponse, filterChain);
				return;
			}
			// Proceed without invoking this filter...
			filterChain.doFilter(request, response);
		}
		else {
			// Do invoke this filter...
			request.setAttribute(alreadyFilteredAttributeName, Boolean.TRUE);
			try {
				doFilterInternal(httpRequest, httpResponse, filterChain);
			}
			finally {
				// Remove the "already filtered" request attribute for this request.
				request.removeAttribute(alreadyFilteredAttributeName);
			}
		}
	}

	/**
	 * Return the name of the request attribute that identifies that a request is already
	 * filtered.
	 * <p>
	 * The default implementation takes the configured name of the concrete filter
	 * instance and appends ".FILTERED". If the filter is not fully initialized, it falls
	 * back to its class name.
	 * @return the name of request attribute indicating already filtered request
	 * @see #ALREADY_FILTERED_SUFFIX
	 */
	protected String getAlreadyFilteredAttributeName() {
		return this.alreadyFilteredAttributeName;
	}

	/**
	 * Typically an ERROR dispatch happens after the REQUEST dispatch completes, and the
	 * filter chain starts anew. On some servers however the ERROR dispatch may be nested
	 * within the REQUEST dispatch, e.g. as a result of calling {@code sendError} on the
	 * response. In that case we are still in the filter chain, on the same thread, but
	 * the request and response have been switched to the original, unwrapped ones.
	 * <p>
	 * Sub-classes may use this method to filter such nested ERROR dispatches and re-apply
	 * wrapping on the request or response. {@code ThreadLocal} context, if any, should
	 * still be active as we are still nested within the filter chain.
	 * @param request the request
	 * @param response the response
	 * @param filterChain the filter chain
	 * @throws ServletException if request is not HTTP request
	 * @throws IOException in case of I/O operation exception
	 */
	protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		doFilter(request, response, filterChain);
	}

	/**
	 * Same contract as for {@code doFilter}, but guaranteed to be just invoked once per
	 * request within a single request thread.
	 * <p>
	 * Provides HttpServletRequest and HttpServletResponse arguments instead of the
	 * default ServletRequest and ServletResponse ones.
	 * @param request the request
	 * @param response the response
	 * @param filterChain the FilterChain
	 * @throws ServletException thrown when a non-I/O exception has occurred
	 * @throws IOException thrown when an I/O exception of some sort has occurred
	 * @see Filter#doFilter
	 */
	protected abstract void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException;

	@Override
	public void init(FilterConfig config) {
	}

	@Override
	public void destroy() {
	}

}
