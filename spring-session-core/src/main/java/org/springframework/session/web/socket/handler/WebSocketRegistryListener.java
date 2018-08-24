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

package org.springframework.session.web.socket.handler;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.web.socket.events.SessionConnectEvent;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * <p>
 * Keeps track of mapping the Spring Session ID to the {@link WebSocketSession} and
 * ensuring when a {@link SessionDestroyedEvent} is fired that the
 * {@link WebSocketSession} is closed.
 * </p>
 *
 *
 * @author Rob Winch
 * @author Mark Anderson
 * @since 1.0
 */
public final class WebSocketRegistryListener
		implements ApplicationListener<ApplicationEvent> {

	private static final Log logger = LogFactory.getLog(WebSocketRegistryListener.class);

	static final CloseStatus SESSION_EXPIRED_STATUS = new CloseStatus(
			CloseStatus.POLICY_VIOLATION.getCode(),
			"This connection was established under an authenticated HTTP Session that has expired");

	private final ConcurrentHashMap<String, Map<String, WebSocketSession>> httpSessionIdToWsSessions = new ConcurrentHashMap<>();

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof SessionDestroyedEvent) {
			SessionDestroyedEvent e = (SessionDestroyedEvent) event;
			closeWsSessions(e.getSessionId());
		}
		else if (event instanceof SessionConnectEvent) {
			SessionConnectEvent e = (SessionConnectEvent) event;
			afterConnectionEstablished(e.getWebSocketSession());
		}
		else if (event instanceof SessionDisconnectEvent) {
			SessionDisconnectEvent e = (SessionDisconnectEvent) event;
			Map<String, Object> sessionAttributes = SimpMessageHeaderAccessor
					.getSessionAttributes(e.getMessage().getHeaders());
			String httpSessionId = (sessionAttributes != null)
					? SessionRepositoryMessageInterceptor.getSessionId(sessionAttributes)
					: null;
			afterConnectionClosed(httpSessionId, e.getSessionId());
		}
	}

	private void afterConnectionEstablished(WebSocketSession wsSession) {
		Principal principal = wsSession.getPrincipal();
		if (principal == null) {
			return;
		}

		String httpSessionId = getHttpSessionId(wsSession);
		registerWsSession(httpSessionId, wsSession);
	}

	private String getHttpSessionId(WebSocketSession wsSession) {
		Map<String, Object> attributes = wsSession.getAttributes();
		return SessionRepositoryMessageInterceptor.getSessionId(attributes);
	}

	private void afterConnectionClosed(String httpSessionId, String wsSessionId) {
		if (httpSessionId == null) {
			return;
		}

		Map<String, WebSocketSession> sessions = this.httpSessionIdToWsSessions
				.get(httpSessionId);
		if (sessions != null) {
			boolean result = sessions.remove(wsSessionId) != null;
			if (logger.isDebugEnabled()) {
				logger.debug("Removal of " + wsSessionId + " was " + result);
			}
			if (sessions.isEmpty()) {
				this.httpSessionIdToWsSessions.remove(httpSessionId);
				if (logger.isDebugEnabled()) {
					logger.debug("Removed the corresponding HTTP Session for "
							+ wsSessionId + " since it contained no WebSocket mappings");
				}
			}
		}
	}

	private void registerWsSession(String httpSessionId, WebSocketSession wsSession) {
		Map<String, WebSocketSession> sessions = this.httpSessionIdToWsSessions
				.get(httpSessionId);
		if (sessions == null) {
			sessions = new ConcurrentHashMap<>();
			this.httpSessionIdToWsSessions.putIfAbsent(httpSessionId, sessions);
			sessions = this.httpSessionIdToWsSessions.get(httpSessionId);
		}
		sessions.put(wsSession.getId(), wsSession);
	}

	private void closeWsSessions(String httpSessionId) {
		Map<String, WebSocketSession> sessionsToClose = this.httpSessionIdToWsSessions
				.remove(httpSessionId);
		if (sessionsToClose == null) {
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug(
					"Closing WebSocket connections associated to expired HTTP Session "
							+ httpSessionId);
		}
		for (WebSocketSession toClose : sessionsToClose.values()) {
			try {
				toClose.close(SESSION_EXPIRED_STATUS);
			}
			catch (IOException ex) {
				logger.debug(
						"Failed to close WebSocketSession (this is nothing to worry about but for debugging only)",
						ex);
			}
		}
	}
}
