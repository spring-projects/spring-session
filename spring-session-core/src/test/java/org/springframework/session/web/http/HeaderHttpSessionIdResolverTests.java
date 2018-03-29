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

import java.util.Collections;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HeaderHttpSessionIdResolver}.
 */
public class HeaderHttpSessionIdResolverTests {

	private static final String HEADER_X_AUTH_TOKEN = "X-Auth-Token";

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private HeaderHttpSessionIdResolver resolver;

	@Before
	public void setup() {
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.resolver = HeaderHttpSessionIdResolver.xAuthToken();
	}

	@Test
	public void createResolverWithXAuthTokenHeader() {
		HeaderHttpSessionIdResolver resolver = HeaderHttpSessionIdResolver.xAuthToken();
		assertThat(ReflectionTestUtils.getField(resolver, "headerName"))
				.isEqualTo("X-Auth-Token");
	}

	@Test
	public void createResolverWithAuthenticationInfoHeader() {
		HeaderHttpSessionIdResolver resolver = HeaderHttpSessionIdResolver
				.authenticationInfo();
		assertThat(ReflectionTestUtils.getField(resolver, "headerName"))
				.isEqualTo("Authentication-Info");
	}

	@Test
	public void createResolverWithCustomHeaderName() {
		HeaderHttpSessionIdResolver resolver = new HeaderHttpSessionIdResolver(
				"Custom-Header");
		assertThat(ReflectionTestUtils.getField(resolver, "headerName"))
				.isEqualTo("Custom-Header");
	}

	@Test
	public void createResolverWithNullHeaderName() {
		assertThatThrownBy(() -> new HeaderHttpSessionIdResolver(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("headerName cannot be null");
	}

	@Test
	public void getRequestedSessionIdNull() {
		assertThat(this.resolver.resolveSessionIds(this.request)).isEmpty();
	}

	@Test
	public void getRequestedSessionIdNotNull() {
		String sessionId = UUID.randomUUID().toString();
		setSessionId(sessionId);
		assertThat(this.resolver.resolveSessionIds(this.request))
				.isEqualTo(Collections.singletonList(sessionId));
	}

	@Test
	public void onNewSession() {
		String sessionId = UUID.randomUUID().toString();
		this.resolver.setSessionId(this.request, this.response, sessionId);
		assertThat(getSessionId()).isEqualTo(sessionId);
	}

	@Test
	public void onDeleteSession() {
		this.resolver.expireSession(this.request, this.response);
		assertThat(getSessionId()).isEmpty();
	}

	// the header is set as apposed to added
	@Test
	public void onNewSessionMulti() {
		String sessionId = UUID.randomUUID().toString();
		this.resolver.setSessionId(this.request, this.response, sessionId);
		this.resolver.setSessionId(this.request, this.response, sessionId);
		assertThat(this.response.getHeaders(HEADER_X_AUTH_TOKEN).size()).isEqualTo(1);
		assertThat(this.response.getHeaders(HEADER_X_AUTH_TOKEN)).containsOnly(sessionId);
	}

	// the header is set as apposed to added
	@Test
	public void onDeleteSessionMulti() {
		this.resolver.expireSession(this.request, this.response);
		this.resolver.expireSession(this.request, this.response);
		assertThat(this.response.getHeaders(HEADER_X_AUTH_TOKEN).size()).isEqualTo(1);
		assertThat(getSessionId()).isEmpty();
	}

	private void setSessionId(String sessionId) {
		this.request.addHeader(HEADER_X_AUTH_TOKEN, sessionId);
	}

	private String getSessionId() {
		return this.response.getHeader(HEADER_X_AUTH_TOKEN);
	}

}
