/*
 * Copyright 2014-present the original author or authors.
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
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.WebUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OncePerRequestFilterTests {

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private MockFilterChain chain;

	private OncePerRequestFilter filter;

	private HttpServlet servlet;

	private List<OncePerRequestFilter> invocations;

	@BeforeEach
	@SuppressWarnings("serial")
	void setup() {
		this.servlet = new HttpServlet() {
		};
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.chain = new MockFilterChain();
		this.invocations = new ArrayList<>();
		this.filter = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {
				OncePerRequestFilterTests.this.invocations.add(this);
				filterChain.doFilter(request, response);
			}
		};
	}

	@Test
	void doFilterOnce() throws ServletException, IOException {
		this.filter.doFilter(this.request, this.response, this.chain);

		assertThat(this.invocations).containsOnly(this.filter);
	}

	@Test
	void doFilterMultiOnlyInvokesOnce() throws ServletException, IOException {
		this.filter.doFilter(this.request, this.response, new MockFilterChain(this.servlet, this.filter));

		assertThat(this.invocations).containsOnly(this.filter);
	}

	@Test
	void doFilterOtherSubclassInvoked() throws ServletException, IOException {
		OncePerRequestFilter filter2 = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {
				OncePerRequestFilterTests.this.invocations.add(this);
				filterChain.doFilter(request, response);
			}
		};
		this.filter.doFilter(this.request, this.response, new MockFilterChain(this.servlet, filter2));

		assertThat(this.invocations).containsOnly(this.filter, filter2);
	}

	@Test // gh-1470
	void filterNestedErrorDispatch() throws ServletException, IOException {
		TestOncePerRequestFilter filter = new TestOncePerRequestFilter();
		this.request.setAttribute(filter.getAlreadyFilteredAttributeName(), Boolean.TRUE);
		this.request.setDispatcherType(DispatcherType.ERROR);
		this.request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, "/error");
		filter.doFilter(this.request, new MockHttpServletResponse(), this.chain);
		assertThat(filter.didFilter).isFalse();
		assertThat(filter.didFilterNestedErrorDispatch).isTrue();
	}

	private static class TestOncePerRequestFilter extends OncePerRequestFilter {

		private boolean didFilter;

		private boolean didFilterNestedErrorDispatch;

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) {
			this.didFilter = true;
		}

		@Override
		protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) {
			this.didFilterNestedErrorDispatch = true;
		}

	}

}
