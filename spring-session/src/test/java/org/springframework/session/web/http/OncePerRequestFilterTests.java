/*
 * Copyright 2002-2015 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.*;

public class OncePerRequestFilterTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain chain;
	private OncePerRequestFilter filter;
	private HttpServlet servlet;


	private List<OncePerRequestFilter> invocations;

	@Before
	@SuppressWarnings("serial")
	public void setup() {
		servlet = new HttpServlet() {};
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
		invocations = new ArrayList<OncePerRequestFilter>();
		filter = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
				invocations.add(this);
				filterChain.doFilter(request, response);
			}
		};
	}

	@Test
	public void doFilterOnce() throws ServletException, IOException {
		filter.doFilter(request, response, chain);

		assertThat(invocations).containsOnly(filter);
	}

	@Test
	public void doFilterMultiOnlyIvokesOnce() throws ServletException, IOException {
		filter.doFilter(request, response, new MockFilterChain(servlet, filter));

		assertThat(invocations).containsOnly(filter);
	}

	@Test
	public void doFilterOtherSubclassInvoked() throws ServletException, IOException {
		OncePerRequestFilter filter2 = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
				invocations.add(this);
				filterChain.doFilter(request, response);
			}
		};
		filter.doFilter(request, response, new MockFilterChain(servlet, filter2));

		assertThat(invocations).containsOnly(filter, filter2);
	}
}