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

import org.junit.Before;
import org.junit.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import static org.assertj.core.api.Assertions.assertThat;

public class HeaderSessionStrategyTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	private HeaderHttpSessionStrategy strategy;
	private String headerName;
	private Session session;

	@Before
	public void setup() throws Exception {
		this.headerName = "x-auth-token";
		this.session = new MapSession();
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.strategy = new HeaderHttpSessionStrategy();
	}

	@Test
	public void getRequestedSessionIdNull() throws Exception {
		assertThat(this.strategy.getRequestedSessionId(this.request)).isNull();
	}

	@Test
	public void getRequestedSessionIdNotNull() throws Exception {
		setSessionId(this.session.getId());
		assertThat(this.strategy.getRequestedSessionId(this.request))
				.isEqualTo(this.session.getId());
	}

	@Test
	public void getRequestedSessionIdNotNullCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		setSessionId(this.session.getId());
		assertThat(this.strategy.getRequestedSessionId(this.request))
				.isEqualTo(this.session.getId());
	}

	@Test
	public void onNewSession() throws Exception {
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId()).isEqualTo(this.session.getId());
	}

	// the header is set as apposed to added
	@Test
	public void onNewSessionMulti() throws Exception {
		this.strategy.onNewSession(this.session, this.request, this.response);
		this.strategy.onNewSession(this.session, this.request, this.response);

		assertThat(this.response.getHeaders(this.headerName).size()).isEqualTo(1);
		assertThat(this.response.getHeaders(this.headerName))
				.containsOnly(this.session.getId());
	}

	@Test
	public void onNewSessionCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId()).isEqualTo(this.session.getId());
	}

	@Test
	public void onDeleteSession() throws Exception {
		this.strategy.onInvalidateSession(this.request, this.response);
		assertThat(getSessionId()).isEmpty();
	}

	// the header is set as apposed to added
	@Test
	public void onDeleteSessionMulti() throws Exception {
		this.strategy.onInvalidateSession(this.request, this.response);
		this.strategy.onInvalidateSession(this.request, this.response);

		assertThat(this.response.getHeaders(this.headerName).size()).isEqualTo(1);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		this.strategy.onInvalidateSession(this.request, this.response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void setHeaderNameNull() throws Exception {
		this.strategy.setHeaderName(null);
	}

	public void setHeaderName(String headerName) {
		this.strategy.setHeaderName(headerName);
		this.headerName = headerName;
	}

	public void setSessionId(String id) {
		this.request.addHeader(this.headerName, id);
	}

	public String getSessionId() {
		return this.response.getHeader(this.headerName);
	}
}
