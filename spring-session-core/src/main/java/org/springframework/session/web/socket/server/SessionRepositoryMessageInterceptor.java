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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * <p>
 * Acts as a {@link ChannelInterceptor} and a {@link HandshakeInterceptor} to ensure the
 * {@link Session#getLastAccessedTime()} is up to date.
 * </p>
 * <ul>
 * <li>Associates the {@link Session#getId()} with the WebSocket Session attributes when
 * the handshake is performed. This is later used when intercepting messages to ensure the
 * {@link Session#getLastAccessedTime()} is updated.</li>
 * <li>Intercepts {@link Message}'s that are have {@link SimpMessageType} that corresponds
 * to {@link #setMatchingMessageTypes(Set)} and updates the last accessed time of the
 * {@link Session}. If the {@link Session} is expired, the {@link Message} is prevented
 * from proceeding.</li>
 * </ul>
 *
 * <p>
 * In order to work {@link SessionRepositoryMessageInterceptor} must be registered as a
 * {@link ChannelInterceptor} and a {@link HandshakeInterceptor} .
 * </p>
 *
 * @param <S> the {@link Session} type
 * @author Rob Winch
 * @since 1.0
 */
public final class SessionRepositoryMessageInterceptor<S extends Session>
		implements ChannelInterceptor, HandshakeInterceptor {

	private static final String SPRING_SESSION_ID_ATTR_NAME = "SPRING.SESSION.ID";

	private final SessionRepository<S> sessionRepository;

	private Set<SimpMessageType> matchingMessageTypes;

	/**
	 * Creates a new instance.
	 *
	 * @param sessionRepository the {@link SessionRepository} to use. Cannot be null.
	 */
	public SessionRepositoryMessageInterceptor(SessionRepository<S> sessionRepository) {
		Assert.notNull(sessionRepository, "sessionRepository cannot be null");
		this.sessionRepository = sessionRepository;
		this.matchingMessageTypes = EnumSet.of(SimpMessageType.CONNECT,
				SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE,
				SimpMessageType.UNSUBSCRIBE);
	}

	/**
	 * <p>
	 * Sets the {@link SimpMessageType} to match on. If the {@link Message} matches, then
	 * {@link #preSend(Message, MessageChannel)} ensures the {@link Session} is not
	 * expired and updates the {@link Session#getLastAccessedTime()}
	 * </p>
	 *
	 * <p>
	 * The default is: SimpMessageType.CONNECT, SimpMessageType.MESSAGE,
	 * SimpMessageType.SUBSCRIBE, SimpMessageType.UNSUBSCRIBE.
	 * </p>
	 *
	 * @param matchingMessageTypes the {@link SimpMessageType} to match on in
	 * {@link #preSend(Message, MessageChannel)}, else the {@link Message} is continued
	 * without accessing or updating the {@link Session}
	 */
	public void setMatchingMessageTypes(Set<SimpMessageType> matchingMessageTypes) {
		Assert.notEmpty(matchingMessageTypes,
				"matchingMessageTypes cannot be null or empty");
		this.matchingMessageTypes = matchingMessageTypes;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (message == null) {
			return message;
		}
		SimpMessageType messageType = SimpMessageHeaderAccessor
				.getMessageType(message.getHeaders());
		if (!this.matchingMessageTypes.contains(messageType)) {
			return message;
		}
		Map<String, Object> sessionHeaders = SimpMessageHeaderAccessor
				.getSessionAttributes(message.getHeaders());
		String sessionId = (sessionHeaders != null)
				? (String) sessionHeaders.get(SPRING_SESSION_ID_ATTR_NAME)
				: null;
		if (sessionId != null) {
			S session = this.sessionRepository.findById(sessionId);
			if (session != null) {
				// update the last accessed time
				session.setLastAccessedTime(Instant.now());
				this.sessionRepository.save(session);
			}
		}
		return message;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
		if (request instanceof ServletServerHttpRequest) {
			ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
			HttpSession session = servletRequest.getServletRequest().getSession(false);
			if (session != null) {
				setSessionId(attributes, session.getId());
			}
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
	}

	public static String getSessionId(Map<String, Object> attributes) {
		return (String) attributes.get(SPRING_SESSION_ID_ATTR_NAME);
	}

	public static void setSessionId(Map<String, Object> attributes, String sessionId) {
		attributes.put(SPRING_SESSION_ID_ATTR_NAME, sessionId);
	}
}
