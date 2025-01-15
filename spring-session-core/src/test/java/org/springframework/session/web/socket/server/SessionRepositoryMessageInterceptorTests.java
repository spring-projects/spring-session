/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.session.web.socket.server;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SessionRepositoryMessageInterceptorTests {

	@Mock(strictness = Mock.Strictness.LENIENT)
	SessionRepository<Session> sessionRepository;

	@Mock
	MessageChannel channel;

	@Mock
	Session session;

	private Message<?> createMessage;

	private SimpMessageHeaderAccessor headers;

	private SessionRepositoryMessageInterceptor<Session> interceptor;

	@BeforeEach
	void setup() {
		this.interceptor = new SessionRepositoryMessageInterceptor<>(this.sessionRepository);
		this.headers = SimpMessageHeaderAccessor.create();
		this.headers.setSessionId("session");
		this.headers.setSessionAttributes(new HashMap<>());
		setMessageType(SimpMessageType.MESSAGE);
		String sessionId = "http-session";
		setSessionId(sessionId);
		given(this.sessionRepository.findById(sessionId)).willReturn(this.session);
	}

	@Test
	void preSendconstructorNullRepository() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SessionRepositoryMessageInterceptor<>(null))
			.withMessage("sessionRepository cannot be null");
	}

	@Test
	void preSendNullMessage() {
		assertThat(this.interceptor.preSend(null, this.channel)).isNull();
	}

	@Test
	void preSendConnectAckDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.CONNECT_ACK);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void preSendHeartbeatDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.HEARTBEAT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void preSendDisconnectDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.DISCONNECT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void preSendOtherDoesNotInvokeSessionRepository() {
		setMessageType(SimpMessageType.OTHER);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void setMatchingMessageTypesNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.interceptor.setMatchingMessageTypes(null))
			.withMessage("matchingMessageTypes cannot be null or empty");
	}

	@Test
	void setMatchingMessageTypesEmpty() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.interceptor.setMatchingMessageTypes(Collections.emptySet()))
			.withMessage("matchingMessageTypes cannot be null or empty");
	}

	@Test
	void preSendSetMatchingMessageTypes() {
		this.interceptor.setMatchingMessageTypes(EnumSet.of(SimpMessageType.DISCONNECT));
		setMessageType(SimpMessageType.DISCONNECT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verify(this.sessionRepository).findById(anyString());
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	void preSendConnectUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.CONNECT);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	void preSendMessageUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.MESSAGE);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	void preSendSubscribeUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.SUBSCRIBE);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	@Test
	void preSendUnsubscribeUpdatesLastUpdateTime() {
		setMessageType(SimpMessageType.UNSUBSCRIBE);
		this.session.setLastAccessedTime(Instant.EPOCH);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verify(this.session).setLastAccessedTime(argThat(isAlmostNow()));
		verify(this.sessionRepository).save(this.session);
	}

	// This will updated when SPR-12288 is resolved
	@Test
	void preSendExpiredSession() {
		setSessionId("expired");

		this.interceptor.preSend(createMessage(), this.channel);

		verify(this.sessionRepository, times(0)).save(any(Session.class));
	}

	@Test
	void preSendNullSessionId() {
		setSessionId(null);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void preSendNullSessionAttributes() {
		this.headers.setSessionAttributes(null);

		assertThat(this.interceptor.preSend(createMessage(), this.channel)).isSameAs(this.createMessage);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void beforeHandshakeNotServletServerHttpRequest() {
		assertThat(this.interceptor.beforeHandshake(null, null, null, null)).isTrue();

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void beforeHandshakeNullSession() {
		ServletServerHttpRequest request = new ServletServerHttpRequest(new MockHttpServletRequest());
		assertThat(this.interceptor.beforeHandshake(request, null, null, null)).isTrue();

		verifyNoMoreInteractions(this.sessionRepository);
	}

	@Test
	void beforeHandshakeSession() {
		MockHttpServletRequest httpRequest = new MockHttpServletRequest();
		HttpSession httpSession = httpRequest.getSession();
		ServletServerHttpRequest request = new ServletServerHttpRequest(httpRequest);
		Map<String, Object> attributes = new HashMap<>();

		assertThat(this.interceptor.beforeHandshake(request, null, null, attributes)).isTrue();

		assertThat(attributes.size()).isEqualTo(1);
		assertThat(SessionRepositoryMessageInterceptor.getSessionId(attributes)).isEqualTo(httpSession.getId());
	}

	/**
	 * At the moment there is no need for afterHandshake to do anything.
	 */
	@Test
	void afterHandshakeDoesNothing() {
		this.interceptor.afterHandshake(null, null, null, null);

		verifyNoMoreInteractions(this.sessionRepository);
	}

	private void setSessionId(String id) {
		SessionRepositoryMessageInterceptor.setSessionId(this.headers.getSessionAttributes(), id);
	}

	private Message<?> createMessage() {
		this.createMessage = MessageBuilder.createMessage("", this.headers.getMessageHeaders());
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
