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

package org.springframework.session.data.redis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * Listen for Redis {@link Message} notifications. If it is a "del" translate into a
 * {@link SessionDeletedEvent}. If it is an "expired" translate into a
 * {@link SessionExpiredEvent}.
 *
 * @author Rob Winch
 * @author Mark Anderson
 * @since 1.0
 * @deprecated Use {@link RedisOperationsSessionRepository} instead.
 */
@Deprecated
public class SessionMessageListener implements MessageListener {
	private static final Log logger = LogFactory.getLog(SessionMessageListener.class);

	private final ApplicationEventPublisher eventPublisher;

	/**
	 * Creates a new instance.
	 *
	 * @param eventPublisher the {@link ApplicationEventPublisher} to use. Cannot be null.
	 */
	public SessionMessageListener(ApplicationEventPublisher eventPublisher) {
		Assert.notNull(eventPublisher, "eventPublisher cannot be null");
		this.eventPublisher = eventPublisher;
	}

	public void onMessage(Message message, byte[] pattern) {
		byte[] messageChannel = message.getChannel();
		byte[] messageBody = message.getBody();
		if (messageChannel == null || messageBody == null) {
			return;
		}
		String channel = new String(messageChannel);
		if (!(channel.endsWith(":del") || channel.endsWith(":expired"))) {
			return;
		}
		String body = new String(messageBody);
		if (!body.startsWith("spring:session:sessions:")) {
			return;
		}

		int beginIndex = body.lastIndexOf(":") + 1;
		int endIndex = body.length();
		String sessionId = body.substring(beginIndex, endIndex);

		if (logger.isDebugEnabled()) {
			logger.debug("Publishing SessionDestroyedEvent for session " + sessionId);
		}

		if (channel.endsWith(":del")) {
			publishEvent(new SessionDeletedEvent(this, sessionId));
		}
		else {
			publishEvent(new SessionExpiredEvent(this, sessionId));
		}
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
