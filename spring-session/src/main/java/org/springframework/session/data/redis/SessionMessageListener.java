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
package org.springframework.session.data.redis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.util.Assert;

/**
 * Listen for Redis {@link Message} notifications. If it is a "del" or "expired"
 * translate into a {@link SessionDestroyedEvent}.
 *
 * @author Rob Winch
 * @since 1.0
 */
public class SessionMessageListener implements MessageListener {
	private static final Log logger = LogFactory.getLog(SessionMessageListener.class);

	private final ApplicationEventPublisher eventPublisher;
	private String boundedHashKeyPrefix;

	/**
	 * Creates a new instance
	 *
	 * @param eventPublisher the {@link ApplicationEventPublisher} to use. Cannot be null.
	 */
	public SessionMessageListener(ApplicationEventPublisher eventPublisher, String boundedHashKeyPrefix) {
		Assert.notNull(eventPublisher, "eventPublisher cannot be null");
		this.eventPublisher = eventPublisher;
		if (boundedHashKeyPrefix == null) {
			this.boundedHashKeyPrefix = "spring:session:sessions:";
		}
		this.boundedHashKeyPrefix = boundedHashKeyPrefix;
	}

	public void onMessage(Message message, byte[] pattern) {
		byte[] messageChannel = message.getChannel();
		byte[] messageBody = message.getBody();
		if(messageChannel == null || messageBody == null) {
			return;
		}
		String channel = new String(messageChannel);
		if(!(channel.endsWith(":del") || channel.endsWith(":expired"))) {
			return;
		}
		String body = new String(messageBody);
		if(!body.startsWith(this.boundedHashKeyPrefix)) {
			return;
		}

		int beginIndex = body.lastIndexOf(":") + 1;
		int endIndex = body.length();
		String sessionId = body.substring(beginIndex, endIndex);

		if(logger.isDebugEnabled()) {
			logger.debug("Publishing SessionDestroyedEvent for session " + sessionId);
		}

		publishEvent(new SessionDestroyedEvent(this, sessionId));
	}

	private void publishEvent(ApplicationEvent event) {
		try {
			this.eventPublisher.publishEvent(event);
		}
		catch (Throwable ex) {
			logger.error("Error publishing " + event + ".", ex);
		}
	}

}
