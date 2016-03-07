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

package org.springframework.session.web.socket.events;

import org.springframework.context.ApplicationEvent;
import org.springframework.session.web.socket.handler.WebSocketConnectHandlerDecoratorFactory;
import org.springframework.session.web.socket.handler.WebSocketRegistryListener;
import org.springframework.web.socket.WebSocketSession;

/**
 * Similar to Spring {@link org.springframework.web.socket.messaging.SessionConnectEvent}
 * except that it provides access to the {@link WebSocketSession} to allow mapping the
 * Spring Session to the {@link WebSocketSession}.
 *
 * @author Rob Winch
 * @since 1.0
 * @see WebSocketRegistryListener
 * @see WebSocketConnectHandlerDecoratorFactory
 */
@SuppressWarnings("serial")
public class SessionConnectEvent extends ApplicationEvent {

	private final WebSocketSession webSocketSession;

	public SessionConnectEvent(Object source, WebSocketSession webSocketSession) {
		super(source);
		this.webSocketSession = webSocketSession;
	}

	public WebSocketSession getWebSocketSession() {
		return this.webSocketSession;
	}
}
