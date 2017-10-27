/*
 * Copyright 2014-2016 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.Session;
import org.springframework.session.web.socket.events.SessionConnectEvent;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/**
 * Ensures that a {@link SessionConnectEvent} is published in
 * {@link WebSocketHandler#afterConnectionEstablished(WebSocketSession)}. This is
 * necessary so that the {@link WebSocketSession} can be mapped to the corresponding
 * Spring {@link Session} to terminate any {@link WebSocketSession} associated with a
 * Spring {@link Session} that was destroyed.
 *
 * @author Rob Winch
 * @since 1.0
 *
 * @see WebSocketRegistryListener
 */
public final class WebSocketConnectHandlerDecoratorFactory
		implements WebSocketHandlerDecoratorFactory {

	private static final Log logger = LogFactory
			.getLog(WebSocketConnectHandlerDecoratorFactory.class);

	private final ApplicationEventPublisher eventPublisher;

	/**
	 * Creates a new instance.
	 *
	 * @param eventPublisher the {@link ApplicationEventPublisher} to use. Cannot be null.
	 */
	public WebSocketConnectHandlerDecoratorFactory(
			ApplicationEventPublisher eventPublisher) {
		Assert.notNull(eventPublisher, "eventPublisher cannot be null");
		this.eventPublisher = eventPublisher;
	}

	@Override
	public WebSocketHandler decorate(WebSocketHandler handler) {
		return new SessionWebSocketHandler(handler);
	}

	private final class SessionWebSocketHandler extends WebSocketHandlerDecorator {

		SessionWebSocketHandler(WebSocketHandler delegate) {
			super(delegate);
		}

		@Override
		public void afterConnectionEstablished(WebSocketSession wsSession)
				throws Exception {
			super.afterConnectionEstablished(wsSession);

			publishEvent(new SessionConnectEvent(this, wsSession));
		}

		private void publishEvent(ApplicationEvent event) {
			try {
				WebSocketConnectHandlerDecoratorFactory.this.eventPublisher
						.publishEvent(event);
			}
			catch (Throwable ex) {
				logger.error("Error publishing " + event + ".", ex);
			}
		}
	}
}
