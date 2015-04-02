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
package org.springframework.session.web.socket.config.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.socket.handler.WebSocketConnectHandlerDecoratorFactory;
import org.springframework.session.web.socket.handler.WebSocketRegistryListener;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Eases configuration of Web Socket and Spring Session integration.
 *
 * <p>
 * The configuration:
 * </p>
 * <ul>
 * <li>Ensures the the {@link Session} is kept alive on incoming web socket
 * messages.</li>
 * <li>Ensures that Web Socket Sessions are destroyed when a {@link Session} is
 * terminated</li>
 * </ul>
 *
 * <p>Example usage</p>
 *
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableScheduling}
 * {@literal @EnableWebSocketMessageBroker}
 * {@literal public class WebSocketConfig<S extends ExpiringSession> extends AbstractSessionWebSocketMessageBrokerConfigurer<S>} {
 *
 *     {@literal @Override}
 *     protected void configureStompEndpoints(StompEndpointRegistry registry) {
 *         registry.addEndpoint("/messages")
 *                 .withSockJS();
 *     }
 *
 *     {@literal @Override}
 *     public void configureMessageBroker(MessageBrokerRegistry registry) {
 *         registry.enableSimpleBroker("/queue/", "/topic/");
 *         registry.setApplicationDestinationPrefixes("/app");
 *     }
 * }
 * </code>
 *
 * @author Rob Winch
 * @since 1.0
 *
 * @param <S>
 *            the type of ExpiringSession
 */
public abstract class AbstractSessionWebSocketMessageBrokerConfigurer<S extends ExpiringSession> extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired
	@SuppressWarnings("rawtypes")
	private SessionRepository sessionRepository;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(sessionRepositoryInterceptor());
	}

	public final void registerStompEndpoints(StompEndpointRegistry registry) {
		configureStompEndpoints(new SessionStompEndpointRegistry(registry, sessionRepositoryInterceptor()));
	}


	/**
	 * Register STOMP endpoints mapping each to a specific URL and (optionally)
	 * enabling and configuring SockJS fallback options with a
	 * {@link SessionRepositoryMessageInterceptor} automatically added as an
	 * interceptor.
	 *
	 * @param registry
	 *            the {@link StompEndpointRegistry} which automatically has a
	 *            {@link SessionRepositoryMessageInterceptor} added to it.
	 */
	protected abstract void configureStompEndpoints(StompEndpointRegistry registry);

	@Override
	public void configureWebSocketTransport(
			WebSocketTransportRegistration registration) {
		registration.addDecoratorFactory(wsConnectHandlerDecoratorFactory());
	}

	@Bean
	public WebSocketRegistryListener webSocketRegistryListener() {
		return new WebSocketRegistryListener();
	}

	@Bean
	public WebSocketConnectHandlerDecoratorFactory wsConnectHandlerDecoratorFactory() {
		return new WebSocketConnectHandlerDecoratorFactory(eventPublisher);
	}

	@Bean
	@SuppressWarnings("unchecked")
	public SessionRepositoryMessageInterceptor<S> sessionRepositoryInterceptor() {
		return new SessionRepositoryMessageInterceptor<S>(sessionRepository);
	}

	static class SessionStompEndpointRegistry implements StompEndpointRegistry {
		private final StompEndpointRegistry registry;
		private final HandshakeInterceptor interceptor;

		public SessionStompEndpointRegistry(StompEndpointRegistry registry,
				HandshakeInterceptor interceptor) {
			this.registry = registry;
			this.interceptor = interceptor;
		}

		public StompWebSocketEndpointRegistration addEndpoint(String... paths) {
			StompWebSocketEndpointRegistration endpoints = registry.addEndpoint(paths);
			endpoints.addInterceptors(interceptor);
			return endpoints;
		}
	}
}