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

package org.springframework.session.web.socket.server;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
public class SessionRepositoryMessageInterceptorTests {
	@Mock
	SessionRepository<Session> sessionRepository;
	@Mock
	MessageChannel channel;
	@Mock
	Session session;

	Message<?> createMessage;

	SimpMessageHeaderAccessor headers;

	SessionRepositoryMessageInterceptor<Session> interceptor;

	@Before
	public void setup() {
		this.interceptor = new SessionRepositoryMessageInterceptor<>(
				this.sessionRepository);
		this.headers = SimpMessageHeaderAccessor.create();
		this.headers.setSessionId("session");
		this.headers.setSessionAttributes(new HashMap<>());
		setMessageType(SimpMessageType.MESSAGE);
		String sessionId = "http-session";
		setSessionId(sessionId);
		given(this.sessionRepository.findById(sessionId)).willReturn(this.session);
	}

	@Test
	public void preSendconstructorNullRepository() {
		assertThatThrownBy(() -> new SessionRepositoryMessageInterceptor<>(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("sessionRepository cannot be null");
	}

	@Test
	public void preSendNullMessage() {
		assertThat(this.interceptor.preSend(null, this.channel)).isNull();
	}

	@Test
	public void preSendConnectAckDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.CONNECT_ACK);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void preSendHeartbeatDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.HEARTBEAT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void preSendDisconnectDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.DISCONNECT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void preSendOtherDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.OTHER);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void setMatchingMessageTypesNull() {
		assertThatThrownBy(() -> this.interceptor.setMatchingMessageTypes(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("matchingMessageTypes cannot be null or empty");
	}

	@Test
	public void setMatchingMessageTypesEmpty() {
		assertThatThrownBy(
				() -> this.interceptor.setMatchingMessageTypes(Collections.emptySet()))
						.isInstanceOf(IllegalArgumentException.class)
						.hasMessage("matchingMessageTypes cannot be null or empty");
	}

	@Test
	public void preSendSetMatchingMessageTypes() {
		this.interceptor.setMatchingMessageTypes(EnumSet.of(SimpMessageType.DISCONNECT));
		setMessageType(SimpMessageType.DISCONNECT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verify(this.sessionRepository).findById(anyString());
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	public void preSendConnectUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.CONNECT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	public void preSendMessageUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.MESSAGE);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	public void preSendSubscribeUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.SUBSCRIBE);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	public void preSendUnsubscribeUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.UNSUBSCRIBE);
		this.session.setLastAccessedTime(Instant.EPOCH);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	// This will updated when SPR-12288 is resolved
	@Test
	public void preSendExpiredSession() {
		setSessionId("expired");

		this.interceptor.preSend(createMessage(), this.channel);

		verify(this.sessionRepository, times(0)).save(any(Session.class));
	}

	@Test
	public void preSendNullSessionId() {
		setSessionId(null);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void preSendNullSessionAttributes() {
		this.headers.setSessionAttributes(null);

		assertThat(this.interceptor.preSend(createMessage(), this.channel))
				.isSameAs(this.createMessage);

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void beforeHandshakeNotServletServerHttpRequest() throws Exception {
		assertThat(this.interceptor.beforeHandshake(null, null, null, null)).isTrue();

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void beforeHandshakeNullSession() throws Exception {
		ServletServerHttpRequest request = new ServletServerHttpRequest(
				new MockHttpServletRequest());
		assertThat(this.interceptor.beforeHandshake(request, null, null, null)).isTrue();

		verifyZeroInteractions(this.sessionRepository);
	}

	@Test
	public void beforeHandshakeSession() throws Exception {
		MockHttpServletRequest httpRequest = new MockHttpServletRequest();
		HttpSession httpSession = httpRequest.getSession();
		ServletServerHttpRequest request = new ServletServerHttpRequest(httpRequest);
		Map<String, Object> attributes = new HashMap<>();

		assertThat(this.interceptor.beforeHandshake(request, null, null, attributes))
				.isTrue();

		assertThat(attributes.size()).isEqualTo(1);
		assertThat(SessionRepositoryMessageInterceptor.getSessionId(attributes))
				.isEqualTo(httpSession.getId());
	}

	/**
	 * At the moment there is no need for afterHandshake to do anything.
	 */
	@Test
	public void afterHandshakeDoesNothing() {
		this.interceptor.afterHandshake(null, null, null, null);

		verifyZeroInteractions(this.sessionRepository);
	}

	private void setSessionId(String id) {
		SessionRepositoryMessageInterceptor
				.setSessionId(this.headers.getSessionAttributes(), id);
	}

	private Message<?> createMessage() {
		this.createMessage = MessageBuilder.createMessage("",
				this.headers.getMessageHeaders());
		return this.createMessage;
	}

	private void setMessageType(SimpMessageType type) {
		this.headers.setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, type);
	}

	static AlmostNowMatcher isAlmostNow() {
		return new AlmostNowMatcher();
	}

	static class AlmostNowMatcher implements ArgumentMatcher<Instant> {

		@Override
		public boolean matches(Instant argument) {
			long now = System.currentTimeMillis();
			long delta = now - argument.toEpochMilli();
			return delta >= 0 && delta < TimeUnit.SECONDS.toMillis(3);
		}

	}
}
