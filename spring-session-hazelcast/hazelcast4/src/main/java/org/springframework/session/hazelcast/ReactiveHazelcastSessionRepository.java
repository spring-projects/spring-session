/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * A {@link ReactiveSessionRepository} implementation using Hazelcast 4 that stores
 * sessions in Hazelcast's distributed {@link IMap} using its {@code *Async} operations.
 *
 * <p>
 * An example of how to create a new instance can be seen below:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * // ... configure Hazelcast ...
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 *
 * ReactiveHazelcastSessionRepository sessionRepository =
 *         new ReactiveHazelcastSessionRepository(hazelcastInstance);
 * </pre>
 *
 * This implementation listens for events on the Hazelcast-backed
 * ReactiveSessionRepository and translates those events into the corresponding Spring
 * Session events. Publish the Spring Session events with the given
 * {@link ApplicationEventPublisher}.
 *
 * <ul>
 * <li>entryAdded - {@link SessionCreatedEvent}</li>
 * <li>entryEvicted - {@link SessionExpiredEvent}</li>
 * <li>entryExpired - {@link SessionExpiredEvent}</li>
 * <li>entryRemoved - {@link SessionDeletedEvent}</li>
 * </ul>
 *
 * @author Eleftheria Stein
 * @author Didier Loiseau
 * @since 2.6.4
 */
public class ReactiveHazelcastSessionRepository
		implements ReactiveSessionRepository<ReactiveHazelcastSessionRepository.HazelcastSession>,
		EntryAddedListener<String, MapSession>, EntryEvictedListener<String, MapSession>,
		EntryRemovedListener<String, MapSession>, EntryExpiredListener<String, MapSession> {

	/**
	 * The default name of map used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_SESSION_MAP_NAME = "spring:session:sessions";

	private static final Log logger = LogFactory.getLog(ReactiveHazelcastSessionRepository.class);

	private final HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher eventPublisher = (event) -> {
	};

	/**
	 * If non-null, this value is used to override
	 * {@link MapSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private IMap<String, MapSession> sessions;

	private UUID sessionListenerId;

	/**
	 * Create a new {@link ReactiveHazelcastSessionRepository} instance.
	 * @param hazelcastInstance the {@link HazelcastInstance} to use for managing sessions
	 */
	public ReactiveHazelcastSessionRepository(HazelcastInstance hazelcastInstance) {
		Assert.notNull(hazelcastInstance, "HazelcastInstance must not be null");
		this.hazelcastInstance = hazelcastInstance;
	}

	@PostConstruct
	public void init() {
		this.sessions = this.hazelcastInstance.getMap(this.sessionMapName);
		this.sessionListenerId = this.sessions.addEntryListener(this, true);
	}

	@PreDestroy
	public void close() {
		this.sessions.removeEntryListener(this.sessionListenerId);
	}

	/**
	 * Sets the {@link ApplicationEventPublisher} that is used to publish
	 * {@link AbstractSessionEvent session events}. The default is to not publish session
	 * events.
	 * @param applicationEventPublisher the {@link ApplicationEventPublisher} that is used
	 * to publish session events. Cannot be null.
	 */
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher cannot be null");
		this.eventPublisher = applicationEventPublisher;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * timeout. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the maximum inactive interval in seconds
	 */
	public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the name of map used to store sessions.
	 * @param sessionMapName the session map name
	 */
	public void setSessionMapName(String sessionMapName) {
		Assert.hasText(sessionMapName, "Map name must not be empty");
		this.sessionMapName = sessionMapName;
	}

	/**
	 * Set the save mode.
	 * @param saveMode the save mode
	 */
	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode must not be null");
		this.saveMode = saveMode;
	}

	@Override
	public Mono<HazelcastSession> createSession() {
		return Mono.defer(() -> {
			MapSession cached = new MapSession();
			if (this.defaultMaxInactiveInterval != null) {
				cached.setMaxInactiveInterval(Duration.ofSeconds(this.defaultMaxInactiveInterval));
			}
			return Mono.just(new HazelcastSession(cached, true));
		});
	}

	@Override
	public Mono<Void> save(HazelcastSession session) {
		CompletionStage<?> result;
		if (session.isNew) {
			result = this.sessions.setAsync(session.getId(), session.getDelegate(),
					session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
		}
		else if (session.sessionIdChanged) {
			result = this.sessions.removeAsync(session.originalId).thenCompose((oldValue) -> {
				session.originalId = session.getId();
				return this.sessions.setAsync(session.getId(), session.getDelegate(),
						session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
			});
		}
		else if (session.hasChanges()) {
			Hazelcast4SessionUpdateEntryProcessor entryProcessor = new Hazelcast4SessionUpdateEntryProcessor();
			if (session.lastAccessedTimeChanged) {
				entryProcessor.setLastAccessedTime(session.getLastAccessedTime());
			}
			if (session.maxInactiveIntervalChanged) {
				entryProcessor.setMaxInactiveInterval(session.getMaxInactiveInterval());
			}
			if (!session.delta.isEmpty()) {
				entryProcessor.setDelta(new HashMap<>(session.delta));
			}
			result = this.sessions.submitToKey(session.getId(), entryProcessor);
		}
		else {
			result = CompletableFuture.completedFuture(null);
		}
		return Mono.fromCompletionStage(result.thenRun(session::clearChangeFlags));
	}

	@Override
	public Mono<HazelcastSession> findById(String id) {
		return Mono.fromCompletionStage(this.sessions.getAsync(id)).flatMap((saved) -> {
			if (saved.isExpired()) {
				return deleteById(saved.getId()).then(Mono.empty());
			}
			return Mono.just(new HazelcastSession(saved, false));
		});
	}

	@Override
	public Mono<Void> deleteById(String id) {
		return Mono.fromCompletionStage(this.sessions.removeAsync(id)).then();
	}

	@Override
	public void entryAdded(EntryEvent<String, MapSession> event) {
		MapSession session = event.getValue();
		if (session.getId().equals(session.getOriginalId())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Session created with id: " + session.getId());
			}
			this.eventPublisher.publishEvent(new SessionCreatedEvent(this, session));
		}
	}

	@Override
	public void entryEvicted(EntryEvent<String, MapSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session expired with id: " + event.getOldValue().getId());
		}
		this.eventPublisher.publishEvent(new SessionExpiredEvent(this, event.getOldValue()));
	}

	@Override
	public void entryRemoved(EntryEvent<String, MapSession> event) {
		MapSession session = event.getOldValue();
		if (session != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Session deleted with id: " + session.getId());
			}
			this.eventPublisher.publishEvent(new SessionDeletedEvent(this, session));
		}
	}

	@Override
	public void entryExpired(EntryEvent<String, MapSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session expired with id: " + event.getOldValue().getId());
		}
		this.eventPublisher.publishEvent(new SessionExpiredEvent(this, event.getOldValue()));
	}

	/**
	 * A custom implementation of {@link Session} that uses a {@link MapSession} as the
	 * basis for its mapping. It keeps track if changes have been made since last save.
	 *
	 * @author Aleksandar Stojsavljevic
	 * @author Didier Loiseau
	 */
	final class HazelcastSession implements Session {

		private final MapSession delegate;

		private boolean isNew;

		private boolean sessionIdChanged;

		private boolean lastAccessedTimeChanged;

		private boolean maxInactiveIntervalChanged;

		private String originalId;

		private final Map<String, Object> delta = new HashMap<>();

		HazelcastSession(MapSession cached, boolean isNew) {
			this.delegate = cached;
			this.isNew = isNew;
			this.originalId = cached.getId();
			if (this.isNew || (ReactiveHazelcastSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
				getAttributeNames()
						.forEach((attributeName) -> this.delta.put(attributeName, cached.getAttribute(attributeName)));
			}
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.lastAccessedTimeChanged = true;
		}

		@Override
		public boolean isExpired() {
			return this.delegate.isExpired();
		}

		@Override
		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		@Override
		public String getId() {
			return this.delegate.getId();
		}

		@Override
		public String changeSessionId() {
			String newSessionId = this.delegate.changeSessionId();
			this.sessionIdChanged = true;
			return newSessionId;
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			Assert.notNull(interval, "interval must not be null");
			this.delegate.setMaxInactiveInterval(interval);
			this.maxInactiveIntervalChanged = true;
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			T attributeValue = this.delegate.getAttribute(attributeName);
			if (attributeValue != null
					&& ReactiveHazelcastSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
				this.delta.put(attributeName, attributeValue);
			}
			return attributeValue;
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
			this.delta.put(attributeName, attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			setAttribute(attributeName, null);
		}

		MapSession getDelegate() {
			return this.delegate;
		}

		boolean hasChanges() {
			return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged || !this.delta.isEmpty());
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.lastAccessedTimeChanged = false;
			this.sessionIdChanged = false;
			this.maxInactiveIntervalChanged = false;
			this.delta.clear();
		}

	}

}
