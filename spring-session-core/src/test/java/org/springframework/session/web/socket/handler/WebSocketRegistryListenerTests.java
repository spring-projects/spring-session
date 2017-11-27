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

package org.springframework.session.web.socket.handler;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.web.socket.events.SessionConnectEvent;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class WebSocketRegistryListenerTests {

	@Mock
	private WebSocketSession wsSession;

	@Mock
	private WebSocketSession wsSession2;

	@Mock
	private Message<byte[]> message;

	@Mock
	private Principal principal;

	private SessionConnectEvent connect;

	private SessionConnectEvent connect2;

	private SessionDisconnectEvent disconnect;

	private SessionDeletedEvent deleted;

	private SessionExpiredEvent expired;

	private Map<String, Object> attributes;

	private WebSocketRegistryListener listener;

	@Before
	public void setup() {
		String sessionId = "session-id";
		MapSession session = new MapSession(sessionId);

		this.attributes = new HashMap<>();
		SessionRepositoryMessageInterceptor.setSessionId(this.attributes, sessionId);

		given(this.wsSession.getAttributes()).willReturn(this.attributes);
		given(this.wsSession.getPrincipal()).willReturn(this.principal);
		given(this.wsSession.getId()).willReturn("wsSession-id");

		given(this.wsSession2.getAttributes()).willReturn(this.attributes);
		given(this.wsSession2.getPrincipal()).willReturn(this.principal);
		given(this.wsSession2.getId()).willReturn("wsSession-id2");

		Map<String, Object> headers = new HashMap<>();
		headers.put(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES, this.attributes);
		given(this.message.getHeaders()).willReturn(new MessageHeaders(headers));

		this.listener = new WebSocketRegistryListener();
		this.connect = new SessionConnectEvent(this.listener, this.wsSession);
		this.connect2 = new SessionConnectEvent(this.listener, this.wsSession2);
		this.disconnect = new SessionDisconnectEvent(this.listener, this.message,
				this.wsSession.getId(), CloseStatus.NORMAL);
		this.deleted = new SessionDeletedEvent(this.listener, session);
		this.expired = new SessionExpiredEvent(this.listener, session);
	}

	@Test
	public void onApplicationEventConnectSessionDeleted() throws Exception {
		this.listener.onApplicationEvent(this.connect);

		this.listener.onApplicationEvent(this.deleted);

		verify(this.wsSession).close(WebSocketRegistryListener.SESSION_EXPIRED_STATUS);
	}

	@Test
	public void onApplicationEventConnectSessionExpired() throws Exception {
		this.listener.onApplicationEvent(this.connect);

		this.listener.onApplicationEvent(this.expired);

		verify(this.wsSession).close(WebSocketRegistryListener.SESSION_EXPIRED_STATUS);
	}

	@Test
	public void onApplicationEventConnectSessionDeletedNullPrincipal() throws Exception {
		given(this.wsSession.getPrincipal()).willReturn(null);
		this.listener.onApplicationEvent(this.connect);

		this.listener.onApplicationEvent(this.deleted);

		verify(this.wsSession, times(0)).close(any(CloseStatus.class));
	}

	@Test
	public void onApplicationEventConnectDisconnect() throws Exception {
		this.listener.onApplicationEvent(this.connect);
		this.listener.onApplicationEvent(this.disconnect);

		this.listener.onApplicationEvent(this.deleted);

		verify(this.wsSession, times(0)).close(any(CloseStatus.class));
	}

	// gh-76
	@Test
	@SuppressWarnings("unchecked")
	public void onApplicationEventConnectDisconnectCleanup() {
		this.listener.onApplicationEvent(this.connect);

		this.listener.onApplicationEvent(this.disconnect);

		Map<String, Map<String, WebSocketSession>> httpSessionIdToWsSessions = (Map<String, Map<String, WebSocketSession>>) ReflectionTestUtils
				.getField(this.listener, "httpSessionIdToWsSessions");
		assertThat(httpSessionIdToWsSessions).isEmpty();
	}

	@Test
	public void onApplicationEventConnectDisconnectNullSession() throws Exception {
		this.listener.onApplicationEvent(this.connect);
		this.attributes.clear();

		this.listener.onApplicationEvent(this.disconnect);

		// no exception
	}

	@Test
	public void onApplicationEventConnectConnectDisonnect() throws Exception {
		this.listener.onApplicationEvent(this.connect);
		this.listener.onApplicationEvent(this.connect2);
		this.listener.onApplicationEvent(this.disconnect);

		this.listener.onApplicationEvent(this.deleted);

		verify(this.wsSession2).close(WebSocketRegistryListener.SESSION_EXPIRED_STATUS);
		verify(this.wsSession, times(0)).close(any(CloseStatus.class));
	}

}
