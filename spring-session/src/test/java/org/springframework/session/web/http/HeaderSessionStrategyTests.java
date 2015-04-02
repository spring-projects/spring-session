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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.web.http.HeaderHttpSessionStrategy;

import static org.fest.assertions.Assertions.assertThat;

public class HeaderSessionStrategyTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	private HeaderHttpSessionStrategy strategy;
	private String headerName;
	private Session session;

	@Before
	public void setup() throws Exception {
		headerName = "x-auth-token";
		session = new MapSession();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		strategy = new HeaderHttpSessionStrategy();
	}

	@Test
	public void getRequestedSessionIdNull() throws Exception {
		assertThat(strategy.getRequestedSessionId(request)).isNull();
	}

	@Test
	public void getRequestedSessionIdNotNull() throws Exception {
		setSessionId(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void getRequestedSessionIdNotNullCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		setSessionId(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void onNewSession() throws Exception {
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	// the header is set as apposed to added
	@Test
	public void onNewSessionMulti() throws Exception {
		strategy.onNewSession(session, request, response);
		strategy.onNewSession(session, request, response);

		assertThat(response.getHeaders(headerName).size()).isEqualTo(1);
		assertThat(response.getHeaders(headerName)).containsOnly(session.getId());
	}

	@Test
	public void onNewSessionCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onDeleteSession() throws Exception {
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}


	// the header is set as apposed to added
	@Test
	public void onDeleteSessionMulti() throws Exception {
		strategy.onInvalidateSession(request, response);
		strategy.onInvalidateSession(request, response);

		assertThat(response.getHeaders(headerName).size()).isEqualTo(1);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void setHeaderNameNull() throws Exception {
		strategy.setHeaderName(null);
	}

	public void setHeaderName(String headerName) {
		strategy.setHeaderName(headerName);
		this.headerName = headerName;
	}

	public void setSessionId(String id) {
		request.addHeader(headerName, id);
	}

	public String getSessionId() {
		return response.getHeader(headerName);
	}
}