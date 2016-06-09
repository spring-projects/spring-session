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

package org.springframework.session.hazelcast;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.ExpiringSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * Listen for events on the Hazelcast-backed SessionRepository and translate those events
 * into the corresponding Spring Session events. Publish the Spring Session events with
 * the given {@link ApplicationEventPublisher}.
 * <ul>
 * <li>entryAdded - {@link SessionCreatedEvent}</li>
 * <li>entryEvicted - {@link SessionExpiredEvent}</li>
 * <li>entryRemoved - {@link SessionDeletedEvent}</li>
 * </ul>
 *
 * @author Tommy Ludwig
 * @author Mark Anderson
 * @since 1.1
 * @deprecated Use {@link HazelcastSessionRepository} instead.
 */
@Deprecated
public class SessionEntryListener implements EntryAddedListener<String, ExpiringSession>,
		EntryEvictedListener<String, ExpiringSession>,
		EntryRemovedListener<String, ExpiringSession> {
	private static final Log logger = LogFactory.getLog(SessionEntryListener.class);

	private ApplicationEventPublisher eventPublisher;

	public SessionEntryListener(ApplicationEventPublisher eventPublisher) {
		Assert.notNull(eventPublisher, "eventPublisher cannot be null");
		this.eventPublisher = eventPublisher;
	}

	public void entryAdded(EntryEvent<String, ExpiringSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session created with id: " + event.getValue().getId());
		}
		this.eventPublisher.publishEvent(new SessionCreatedEvent(this, event.getValue()));
	}

	public void entryEvicted(EntryEvent<String, ExpiringSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session expired with id: " + event.getOldValue().getId());
		}
		this.eventPublisher
				.publishEvent(new SessionExpiredEvent(this, event.getOldValue()));
	}

	public void entryRemoved(EntryEvent<String, ExpiringSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session deleted with id: " + event.getOldValue().getId());
		}
		this.eventPublisher
				.publishEvent(new SessionDeletedEvent(this, event.getOldValue()));
	}

}
