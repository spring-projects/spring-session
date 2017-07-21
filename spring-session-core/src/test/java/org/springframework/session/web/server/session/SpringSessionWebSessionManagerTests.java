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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rob Winch
 * @since 5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SpringSessionWebSessionManagerTests<S extends Session> {
	@Mock
	ReactorSessionRepository<S>  sessions;

	@Mock
	WebSessionIdResolver resolver;

	@Mock
	S createSession;

	@Mock
	S findByIdSession;

	Mono<S> createSessionMono;

	ServerWebExchange exchange = MockServerHttpRequest.get("/").toExchange();


	SpringSessionWebSessionManager manager;

	@Before
	public void setup() {
		when(this.createSession.getId()).thenReturn("createSession-id");
		when(this.findByIdSession.getId()).thenReturn("findByIdSession-id");
		this.createSessionMono = Mono.just(this.createSession);
		when(this.sessions.createSession()).thenReturn(createSessionMono);
		this.manager = new SpringSessionWebSessionManager(this.sessions);
		this.manager.setSessionIdResolver(this.resolver);
	}

	@Test
	public void getSessionWhenDefaultSessionIdResolverFoundSessionUsed() {
		String findByIdSessionId = this.findByIdSession.getId();
		this.exchange = MockServerHttpRequest.get("/").cookie(new HttpCookie("SESSION", findByIdSessionId)).toExchange();
		this.manager = new SpringSessionWebSessionManager(this.sessions);
		when(this.sessions.findById(findByIdSessionId)).thenReturn(Mono.just(findByIdSession));

		WebSession webSession = this.manager.getSession(exchange).block();

		assertThat(webSession.getId()).isEqualTo(findByIdSessionId);
		verify(this.sessions).findById(findByIdSessionId);
	}

	@Test
	public void getSessionWhenNewThenCreateSessionInvoked() {
		WebSession webSession = this.manager.getSession(exchange).block();

		assertThat(webSession.getId()).isEqualTo(this.createSession.getId());
		verify(this.sessions).createSession();
	}

	@Test
	public void getSessionWhenNewAndPutThenSetAttributeInvoked() {
		String attrName = "attrName";
		String attrValue = "attrValue";

		WebSession webSession = this.manager.getSession(exchange).block();
		webSession.getAttributes().put(attrName, attrValue);

		verify(this.createSession).setAttribute(attrName, attrValue);
	}

	@Test
	public void getSessionWhenInvalidIdThenCreateSessionInvoked() {
		String invalidId = "invalid";
		String createSessionId = this.createSession.getId();
		when(this.sessions.findById(any())).thenReturn(Mono.empty());
		when(this.resolver.resolveSessionIds(exchange)).thenReturn(Arrays.asList(invalidId));

		WebSession webSession = this.manager.getSession(exchange).block();

		assertThat(webSession.getId()).isEqualTo(createSessionId);
		verify(this.sessions).findById(invalidId);

		Mono<String> mono = Mono.just("toTest");
		StepVerifier
				.create(mono)
				.expectNoEvent(Duration.ZERO);
	}

	@Test
	public void getSessionWhenValidIdThenFoundSessionUsed() {
		String findByIdSessionId = this.findByIdSession.getId();
		when(this.sessions.findById(findByIdSessionId)).thenReturn(Mono.just(findByIdSession));
		when(this.resolver.resolveSessionIds(exchange)).thenReturn(Arrays.asList(findByIdSessionId));

		WebSession webSession = this.manager.getSession(exchange).block();

		assertThat(webSession.getId()).isEqualTo(findByIdSessionId);
		verify(this.sessions).findById(findByIdSessionId);
	}
}