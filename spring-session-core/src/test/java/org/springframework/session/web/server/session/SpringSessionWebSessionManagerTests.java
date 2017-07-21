/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.web.server.session;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionIdResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringSessionWebSessionManager}.
 *
 * @author Rob Winch
 * @since 5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SpringSessionWebSessionManagerTests<S extends Session> {

	@Mock
	private ReactorSessionRepository<S> sessions;

	@Mock
	private WebSessionIdResolver resolver;

	@Mock
	private S createSession;

	@Mock
	private S findByIdSession;

	private Mono<S> createSessionMono;

	private ServerWebExchange exchange = MockServerHttpRequest.get("/").toExchange();

	private SpringSessionWebSessionManager manager;

	@Before
	public void setup() {
		given(this.createSession.getId()).willReturn("createSession-id");
		given(this.findByIdSession.getId()).willReturn("findByIdSession-id");
		this.createSessionMono = Mono.just(this.createSession);
		given(this.sessions.createSession()).willReturn(this.createSessionMono);
		this.manager = new SpringSessionWebSessionManager(this.sessions);
		this.manager.setSessionIdResolver(this.resolver);
	}

	@Test
	public void getSessionWhenDefaultSessionIdResolverFoundSessionUsed() {
		String findByIdSessionId = this.findByIdSession.getId();
		this.exchange = MockServerHttpRequest.get("/")
				.cookie(new HttpCookie("SESSION", findByIdSessionId)).toExchange();
		this.manager = new SpringSessionWebSessionManager(this.sessions);
		given(this.sessions.findById(findByIdSessionId))
				.willReturn(Mono.just(this.findByIdSession));

		WebSession webSession = this.manager.getSession(this.exchange).block();

		assertThat(webSession.getId()).isEqualTo(findByIdSessionId);
		verify(this.sessions).findById(findByIdSessionId);
	}

	@Test
	public void getSessionWhenNewThenCreateSessionInvoked() {
		WebSession webSession = this.manager.getSession(this.exchange).block();

		assertThat(webSession.getId()).isEqualTo(this.createSession.getId());
		verify(this.sessions).createSession();
	}

	@Test
	public void getSessionWhenNewAndPutThenSetAttributeInvoked() {
		String attrName = "attrName";
		String attrValue = "attrValue";

		WebSession webSession = this.manager.getSession(this.exchange).block();
		webSession.getAttributes().put(attrName, attrValue);

		verify(this.createSession).setAttribute(attrName, attrValue);
	}

	@Test
	public void getSessionWhenInvalidIdThenCreateSessionInvoked() {
		String invalidId = "invalid";
		String createSessionId = this.createSession.getId();
		given(this.sessions.findById(any())).willReturn(Mono.empty());
		given(this.resolver.resolveSessionIds(this.exchange))
				.willReturn(Collections.singletonList(invalidId));

		WebSession webSession = this.manager.getSession(this.exchange).block();

		assertThat(webSession.getId()).isEqualTo(createSessionId);
		verify(this.sessions).findById(invalidId);

		Mono<String> mono = Mono.just("toTest");
		StepVerifier.create(mono).expectNoEvent(Duration.ZERO);
	}

	@Test
	public void getSessionWhenValidIdThenFoundSessionUsed() {
		String findByIdSessionId = this.findByIdSession.getId();
		given(this.sessions.findById(findByIdSessionId))
				.willReturn(Mono.just(this.findByIdSession));
		given(this.resolver.resolveSessionIds(this.exchange))
				.willReturn(Arrays.asList(findByIdSessionId));

		WebSession webSession = this.manager.getSession(this.exchange).block();

		assertThat(webSession.getId()).isEqualTo(findByIdSessionId);
		verify(this.sessions).findById(findByIdSessionId);
	}

}
