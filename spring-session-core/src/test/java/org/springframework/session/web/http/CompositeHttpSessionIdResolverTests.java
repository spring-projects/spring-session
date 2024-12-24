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

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CompositeHttpSessionIdResolver}.
 *
 * @author Yanming Zhou
 */
class CompositeHttpSessionIdResolverTests {

	private static final String HEADER_X_AUTH_TOKEN = "X-Auth-Token";

	private static final String HEADER_AUTHENTICATION_INFO = "Authentication-Info";

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private CompositeHttpSessionIdResolver resolver;

	@BeforeEach
	void setup() {
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.resolver = new CompositeHttpSessionIdResolver(HeaderHttpSessionIdResolver.xAuthToken(),
				HeaderHttpSessionIdResolver.authenticationInfo());
	}

	@Test
	void getRequestedSessionIdNull() {
		assertThat(this.resolver.resolveSessionIds(this.request)).isEmpty();
	}

	@Test
	void resolveSessionIdsByXAuthToken() {
		String sessionId = UUID.randomUUID().toString();
		this.request.addHeader(HEADER_X_AUTH_TOKEN, sessionId);
		assertThat(this.resolver.resolveSessionIds(this.request)).isEqualTo(Collections.singletonList(sessionId));
	}

	@Test
	void resolveSessionIdsByAuthenticationInfo() {
		String sessionId = UUID.randomUUID().toString();
		this.request.addHeader(HEADER_AUTHENTICATION_INFO, sessionId);
		assertThat(this.resolver.resolveSessionIds(this.request)).isEqualTo(Collections.singletonList(sessionId));
	}

	@Test
	void onNewSession() {
		String sessionId = UUID.randomUUID().toString();
		this.resolver.setSessionId(this.request, this.response, sessionId);
		assertThat(this.response.getHeader(HEADER_X_AUTH_TOKEN)).isEqualTo(sessionId);
		assertThat(this.response.getHeader(HEADER_AUTHENTICATION_INFO)).isEqualTo(sessionId);
	}

	@Test
	void onDeleteSession() {
		this.resolver.expireSession(this.request, this.response);
		assertThat(this.response.getHeader(HEADER_X_AUTH_TOKEN)).isEmpty();
		assertThat(this.response.getHeader(HEADER_AUTHENTICATION_INFO)).isEmpty();
	}

}
