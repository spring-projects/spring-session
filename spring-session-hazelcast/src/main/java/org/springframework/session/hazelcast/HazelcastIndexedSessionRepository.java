/*
 * Copyright 2014-2019 the original author or authors.
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.query.Predicates;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link org.springframework.session.SessionRepository} implementation that stores
 * sessions in Hazelcast's distributed {@link IMap}.
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
 * HazelcastIndexedSessionRepository sessionRepository =
 *         new HazelcastIndexedSessionRepository(hazelcastInstance);
 * </pre>
 *
 * In order to support finding sessions by principal name using
 * {@link #findByIndexNameAndIndexValue(String, String)} method, custom configuration of
 * {@code IMap} supplied to this implementation is required.
 *
 * The following snippet demonstrates how to define required configuration using
 * programmatic Hazelcast Configuration:
 *
 * <pre class="code">
 * MapAttributeConfig attributeConfig = new MapAttributeConfig()
 *         .setName(HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
 *         .setExtractor(PrincipalNameExtractor.class.getName());
 *
 * Config config = new Config();
 *
 * config.getMapConfig(HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
 *         .addMapAttributeConfig(attributeConfig)
 *         .addMapIndexConfig(new MapIndexConfig(
 *                 HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE, false));
 *
 * Hazelcast.newHazelcastInstance(config);
 * </pre>
 *
 * This implementation listens for events on the Hazelcast-backed SessionRepository and
 * translates those events into the corresponding Spring Session events. Publish the
 * Spring Session events with the given {@link ApplicationEventPublisher}.
 *
 * <ul>
 * <li>entryAdded - {@link SessionCreatedEvent}</li>
 * <li>entryEvicted - {@link SessionExpiredEvent}</li>
 * <li>entryRemoved - {@link SessionDeletedEvent}</li>
 * </ul>
 *
 * @author Vedran Pavic
 * @author Tommy Ludwig
 * @author Mark Anderson
 * @author Aleksandar Stojsavljevic
 * @since 2.2.0
 */
public class HazelcastIndexedSessionRepository
		implements FindByIndexNameSessionRepository<HazelcastIndexedSessionRepository.HazelcastSession>,
		EntryAddedListener<String, MapSession>, EntryEvictedListener<String, MapSession>,
		EntryRemovedListener<String, MapSession> {

	/**
	 * The default name of map used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_SESSION_MAP_NAME = "spring:session:sessions";

	/**
	 * The principal name custom attribute name.
	 */
	public static final String PRINCIPAL_NAME_ATTRIBUTE = "principalName";

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final boolean SUPPORTS_SET_TTL = ClassUtils.hasAtLeastOneMethodWithName(IMap.class, "setTtl");

	private static final Log logger = LogFactory.getLog(HazelcastIndexedSessionRepository.class);

	private final HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher eventPublisher = (event) -> {
	};

	/**
	 * If non-null, this value is used to override
	 * {@link MapSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(new PrincipalNameIndexResolver<>());

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private IMap<String, MapSession> sessions;

	private UUID sessionListenerId;

	/**
	 * Create a new {@link HazelcastIndexedSessionRepository} instance.
	 * @param hazelcastInstance the {@link HazelcastInstance} to use for managing sessions
	 */
	public HazelcastIndexedSessionRepository(HazelcastInstance hazelcastInstance) {
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
	 * Set the {@link IndexResolver} to use.
	 * @param indexResolver the index resolver
	 */
	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver cannot be null");
		this.indexResolver = indexResolver;
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
	 * Sets the Hazelcast flush mode. Default flush mode is {@link FlushMode#ON_SAVE}.
	 * @param flushMode the new Hazelcast flush mode
	 */
	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode cannot be null");
		this.flushMode = flushMode;
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
	public HazelcastSession createSession() {
		MapSession cached = new MapSession();
		if (this.defaultMaxInactiveInterval != null) {
			cached.setMaxInactiveInterval(Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		HazelcastSession session = new HazelcastSession(cached, true);
		session.flushImmediateIfNecessary();
		return session;
	}

	@Override
	public void save(HazelcastSession session) {
		if (session.isNew) {
			this.sessions.set(session.getId(), session.getDelegate(), session.getMaxInactiveInterval().getSeconds(),
					TimeUnit.SECONDS);
		}
		else if (session.sessionIdChanged) {
			this.sessions.delete(session.originalId);
			session.originalId = session.getId();
			this.sessions.set(session.getId(), session.getDelegate(), session.getMaxInactiveInterval().getSeconds(),
					TimeUnit.SECONDS);
		}
		else if (session.hasChanges()) {
			SessionUpdateEntryProcessor entryProcessor = new SessionUpdateEntryProcessor();
			if (session.lastAccessedTimeChanged) {
				entryProcessor.setLastAccessedTime(session.getLastAccessedTime());
			}
			if (session.maxInactiveIntervalChanged) {
				if (SUPPORTS_SET_TTL) {
					updateTtl(session);
				}
				entryProcessor.setMaxInactiveInterval(session.getMaxInactiveInterval());
			}
			if (!session.delta.isEmpty()) {
				entryProcessor.setDelta(new HashMap<>(session.delta));
			}
			this.sessions.executeOnKey(session.getId(), entryProcessor);
		}
		session.clearChangeFlags();
	}

	private void updateTtl(HazelcastSession session) {
		this.sessions.setTtl(session.getId(), session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
	}

	@Override
	public HazelcastSession findById(String id) {
		MapSession saved = this.sessions.get(id);
		if (saved == null) {
			return null;
		}
		if (saved.isExpired()) {
			deleteById(saved.getId());
			return null;
		}
		return new HazelcastSession(saved, false);
	}

	@Override
	public void deleteById(String id) {
		this.sessions.remove(id);
	}

	@Override
	public Map<String, HazelcastSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		Collection<MapSession> sessions = this.sessions.values(Predicates.equal(PRINCIPAL_NAME_ATTRIBUTE, indexValue));
		Map<String, HazelcastSession> sessionMap = new HashMap<>(sessions.size());
		for (MapSession session : sessions) {
			sessionMap.put(session.getId(), new HazelcastSession(session, false));
		}
		return sessionMap;
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

	/**
	 * A custom implementation of {@link Session} that uses a {@link MapSession} as the
	 * basis for its mapping. It keeps track if changes have been made since last save.
	 *
	 * @author Aleksandar Stojsavljevic
	 */
	final class HazelcastSession implements Session {

		private final MapSession delegate;

		private boolean isNew;

		private boolean sessionIdChanged;

		private boolean lastAccessedTimeChanged;

		private boolean maxInactiveIntervalChanged;

		private String originalId;

		private Map<String, Object> delta = new HashMap<>();

		HazelcastSession(MapSession cached, boolean isNew) {
			this.delegate = cached;
			this.isNew = isNew;
			this.originalId = cached.getId();
			if (this.isNew || (HazelcastIndexedSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
				getAttributeNames()
						.forEach((attributeName) -> this.delta.put(attributeName, cached.getAttribute(attributeName)));
			}
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.lastAccessedTimeChanged = true;
			flushImmediateIfNecessary();
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
			this.delegate.setMaxInactiveInterval(interval);
			this.maxInactiveIntervalChanged = true;
			flushImmediateIfNecessary();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			T attributeValue = this.delegate.getAttribute(attributeName);
			if (attributeValue != null
					&& HazelcastIndexedSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
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
			if (SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				Map<String, String> indexes = HazelcastIndexedSessionRepository.this.indexResolver
						.resolveIndexesFor(this);
				String principal = (attributeValue != null) ? indexes.get(PRINCIPAL_NAME_INDEX_NAME) : null;
				this.delegate.setAttribute(PRINCIPAL_NAME_INDEX_NAME, principal);
			}
			flushImmediateIfNecessary();
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

		private void flushImmediateIfNecessary() {
			if (HazelcastIndexedSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
				HazelcastIndexedSessionRepository.this.save(this);
			}
		}

	}

}
