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

package org.springframework.session.web.socket.config.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.socket.handler.WebSocketConnectHandlerDecoratorFactory;
import org.springframework.session.web.socket.handler.WebSocketRegistryListener;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebMvcStompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UrlPathHelper;

/**
 * Eases configuration of Web Socket and Spring Session integration.
 *
 * <p>
 * The configuration:
 * </p>
 * <ul>
 * <li>Ensures the {@link Session} is kept alive on incoming web socket messages.</li>
 * <li>Ensures that Web Socket Sessions are destroyed when a {@link Session} is terminated
 * </li>
 * </ul>
 *
 * <p>
 * Example usage
 * </p>
 *
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableScheduling}
 * {@literal @EnableWebSocketMessageBroker}
 * {@literal public class WebSocketConfig<S extends Session> extends AbstractSessionWebSocketMessageBrokerConfigurer<S>} {
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
 * @param <S> the type of Session
 * @author Rob Winch
 * @since 1.0
 */
public abstract class AbstractSessionWebSocketMessageBrokerConfigurer<S extends Session>
		implements WebSocketMessageBrokerConfigurer {

	@Autowired
	@SuppressWarnings("rawtypes")
	private SessionRepository sessionRepository;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(sessionRepositoryInterceptor());
	}

	@Override
	public final void registerStompEndpoints(StompEndpointRegistry registry) {
		if (registry instanceof WebMvcStompEndpointRegistry) {
			WebMvcStompEndpointRegistry mvcRegistry = (WebMvcStompEndpointRegistry) registry;
			configureStompEndpoints(new SessionStompEndpointRegistry(mvcRegistry,
					sessionRepositoryInterceptor()));
		}
	}

	/**
	 * Register STOMP endpoints mapping each to a specific URL and (optionally) enabling
	 * and configuring SockJS fallback options with a
	 * {@link SessionRepositoryMessageInterceptor} automatically added as an interceptor.
	 *
	 * @param registry the {@link StompEndpointRegistry} which automatically has a
	 * {@link SessionRepositoryMessageInterceptor} added to it.
	 */
	protected abstract void configureStompEndpoints(StompEndpointRegistry registry);

	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
		registration.addDecoratorFactory(wsConnectHandlerDecoratorFactory());
	}

	@Bean
	public WebSocketRegistryListener webSocketRegistryListener() {
		return new WebSocketRegistryListener();
	}

	@Bean
	public WebSocketConnectHandlerDecoratorFactory wsConnectHandlerDecoratorFactory() {
		return new WebSocketConnectHandlerDecoratorFactory(this.eventPublisher);
	}

	@Bean
	@SuppressWarnings("unchecked")
	public SessionRepositoryMessageInterceptor<S> sessionRepositoryInterceptor() {
		return new SessionRepositoryMessageInterceptor<>(this.sessionRepository);
	}

	/**
	 * A {@link StompEndpointRegistry} that applies {@link HandshakeInterceptor}.
	 */
	static class SessionStompEndpointRegistry implements StompEndpointRegistry {
		private final WebMvcStompEndpointRegistry registry;
		private final HandshakeInterceptor interceptor;

		SessionStompEndpointRegistry(WebMvcStompEndpointRegistry registry,
				HandshakeInterceptor interceptor) {
			this.registry = registry;
			this.interceptor = interceptor;
		}

		@Override
		public StompWebSocketEndpointRegistration addEndpoint(String... paths) {
			StompWebSocketEndpointRegistration endpoints = this.registry
					.addEndpoint(paths);
			endpoints.addInterceptors(this.interceptor);
			return endpoints;
		}

		@Override
		public void setOrder(int order) {
			this.registry.setOrder(order);
		}

		@Override
		public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
			this.registry.setUrlPathHelper(urlPathHelper);
		}

		@Override
		public WebMvcStompEndpointRegistry setErrorHandler(
				StompSubProtocolErrorHandler errorHandler) {
			return this.registry.setErrorHandler(errorHandler);
		}
	}
}
