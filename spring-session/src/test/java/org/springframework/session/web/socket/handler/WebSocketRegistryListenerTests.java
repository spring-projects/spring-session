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
package org.springframework.session.web.socket.handler;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.web.socket.events.SessionConnectEvent;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@RunWith(MockitoJUnitRunner.class)
public class WebSocketRegistryListenerTests {
	@Mock
	WebSocketSession wsSession;
	@Mock
	WebSocketSession wsSession2;
	@Mock
	Message<byte[]> message;
	@Mock
	Principal principal;

	SessionConnectEvent connect;

	SessionConnectEvent connect2;

	SessionDisconnectEvent disconnect;

	SessionDeletedEvent deleted;
	
	SessionExpiredEvent expired;

	Map<String, Object> attributes;

	String sessionId;

	WebSocketRegistryListener listener;


	@Before
	public void setup() {
		sessionId = "session-id";
		attributes = new HashMap<String,Object>();
		SessionRepositoryMessageInterceptor.setSessionId(attributes, sessionId);

		when(wsSession.getAttributes()).thenReturn(attributes);
		when(wsSession.getPrincipal()).thenReturn(principal);
		when(wsSession.getId()).thenReturn("wsSession-id");

		when(wsSession2.getAttributes()).thenReturn(attributes);
		when(wsSession2.getPrincipal()).thenReturn(principal);
		when(wsSession2.getId()).thenReturn("wsSession-id2");

		Map<String,Object> headers = new HashMap<String,Object>();
		headers.put(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES, attributes);
		when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

		listener = new WebSocketRegistryListener();
		connect = new SessionConnectEvent(listener,wsSession);
		connect2 = new SessionConnectEvent(listener,wsSession2);
		disconnect = new SessionDisconnectEvent(listener, message, wsSession.getId(), CloseStatus.NORMAL);
		deleted = new SessionDeletedEvent(listener, sessionId);
		expired = new SessionExpiredEvent(listener, sessionId);
	}

	@Test
	public void onApplicationEventConnectSessionDeleted() throws Exception {
		listener.onApplicationEvent(connect);

		listener.onApplicationEvent(deleted);

		verify(wsSession).close(WebSocketRegistryListener.SESSION_EXPIRED_STATUS);
	}
	
	@Test
	public void onApplicationEventConnectSessionExpired() throws Exception {
		listener.onApplicationEvent(connect);

		listener.onApplicationEvent(expired);

		verify(wsSession).close(WebSocketRegistryListener.SESSION_EXPIRED_STATUS);
	}


	@Test
	public void onApplicationEventConnectSessionDeletedNullPrincipal() throws Exception {
		when(wsSession.getPrincipal()).thenReturn(null);
		listener.onApplicationEvent(connect);

		listener.onApplicationEvent(deleted);

		verify(wsSession,times(0)).close(any(CloseStatus.class));
	}

	@Test
	public void onApplicationEventConnectDisconnect() throws Exception {
		listener.onApplicationEvent(connect);
		listener.onApplicationEvent(disconnect);

		listener.onApplicationEvent(deleted);

		verify(wsSession,times(0)).close(any(CloseStatus.class));
	}

	// gh-76
	@Test
	@SuppressWarnings("unchecked")
	public void onApplicationEventConnectDisconnectCleanup() {
		listener.onApplicationEvent(connect);

		listener.onApplicationEvent(disconnect);

		Map<String,Map<String,WebSocketSession>> httpSessionIdToWsSessions =
				(Map<String, Map<String, WebSocketSession>>) ReflectionTestUtils.getField(listener, "httpSessionIdToWsSessions");
		assertThat(httpSessionIdToWsSessions).isEmpty();
	}

	@Test
	public void onApplicationEventConnectDisconnectNullSession() throws Exception {
		listener.onApplicationEvent(connect);
		attributes.clear();

		listener.onApplicationEvent(disconnect);

		// no exception
	}

	@Test
	public void onApplicationEventConnectConnectDisonnect() throws Exception {
		listener.onApplicationEvent(connect);
		listener.onApplicationEvent(connect2);
		listener.onApplicationEvent(disconnect);

		listener.onApplicationEvent(deleted);

		verify(wsSession2).close(WebSocketRegistryListener.SESSION_EXPIRED_STATUS);
		verify(wsSession,times(0)).close(any(CloseStatus.class));
	}
}
