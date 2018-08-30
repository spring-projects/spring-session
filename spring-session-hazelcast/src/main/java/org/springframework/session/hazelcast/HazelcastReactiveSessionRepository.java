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

package org.springframework.session.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import org.springframework.session.MapSession;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

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
 * HazelcastSessionRepository sessionRepository =
 *         new HazelcastSessionRepository(hazelcastInstance);
 * </pre>
 *
 * The following snippet demonstrates how to define required configuration using
 * programmatic Hazelcast Configuration:
 *
 * <pre class="code">
 * MapAttributeConfig attributeConfig = new MapAttributeConfig()
 *         .setName(HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
 *         .setExtractor(PrincipalNameExtractor.class.getName());
 *
 * Config config = new Config();
 *
 * config.getMapConfig(HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME)
 *         .addMapAttributeConfig(attributeConfig)
 *         .addMapIndexConfig(new MapIndexConfig(
 *                 HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE, false));
 *
 * Hazelcast.newHazelcastInstance(config);
 * </pre>
 *
 * @author Vedran Pavic
 * @author Tommy Ludwig
 * @author Mark Anderson
 * @author Aleksandar Stojsavljevic
 * @since 1.3.0
 */
public class HazelcastReactiveSessionRepository implements
        ReactiveSessionRepository<HazelcastReactiveSessionRepository.HazelcastSession> {

	/**
	 * The default name of map used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_SESSION_MAP_NAME = "spring:session:sessions";

	private static final Log logger = LogFactory.getLog(HazelcastReactiveSessionRepository.class);

	private final HazelcastInstance hazelcastInstance;

	/**
	 * If non-null, this value is used to override
	 * {@link MapSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private HazelcastFlushMode hazelcastFlushMode = HazelcastFlushMode.ON_SAVE;

	private IMap<String, MapSession> sessions;

	public HazelcastReactiveSessionRepository(HazelcastInstance hazelcastInstance) {
		Assert.notNull(hazelcastInstance, "HazelcastInstance must not be null");
		this.hazelcastInstance = hazelcastInstance;
	}

	@PostConstruct
	public void init() {
		this.sessions = this.hazelcastInstance.getMap(this.sessionMapName);
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
	 * Sets the Hazelcast flush mode. Default flush mode is
	 * {@link HazelcastFlushMode#ON_SAVE}.
	 * @param hazelcastFlushMode the new Hazelcast flush mode
	 */
	public void setHazelcastFlushMode(HazelcastFlushMode hazelcastFlushMode) {
		Assert.notNull(hazelcastFlushMode, "HazelcastFlushMode cannot be null");
		this.hazelcastFlushMode = hazelcastFlushMode;
	}

	@Override
	public Mono<HazelcastSession> createSession() {
		HazelcastSession result = new HazelcastSession();
		if (this.defaultMaxInactiveInterval != null) {
			result.setMaxInactiveInterval(
					Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		return Mono.just(result);
	}

	@Override
	public Mono<Void> save(HazelcastSession session) {
		Mono<Void> result = Mono.empty();
		if (session.isNew) {
			result = new MonoICompletableFuture<>(this.sessions.setAsync(session.getId(), session.getDelegate(),
					session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS));
		}
		else if (session.sessionIdChanged) {
			String originalId = session.originalId;
			session.originalId = session.getId();
			result = Flux.merge(
				new MonoICompletableFuture<>(this.sessions.removeAsync(originalId)),
				new MonoICompletableFuture<>(this.sessions.setAsync(session.getId(), session.getDelegate(),
					session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS))
			).then();
		}
		else if (session.hasChanges()) {
			SessionUpdateEntryProcessor entryProcessor = new SessionUpdateEntryProcessor();
			if (session.lastAccessedTimeChanged) {
				entryProcessor.setLastAccessedTime(session.getLastAccessedTime());
			}
			if (session.maxInactiveIntervalChanged) {
				entryProcessor.setMaxInactiveInterval(session.getMaxInactiveInterval());
			}
			if (!session.delta.isEmpty()) {
				entryProcessor.setDelta(session.delta);
			}
			result = new MonoICompletableFuture<>(this.sessions.submitToKey(session.getId(), entryProcessor));
		}
		return result.then(Mono.fromRunnable(session::clearChangeFlags));
	}

	@Override
	public Mono<HazelcastSession> findById(String id) {
		return new MonoICompletableFuture<>(this.sessions.getAsync(id))
			.flatMap(saved -> {
				if (saved.isExpired()) {
					return deleteById(saved.getId()).then(Mono.empty());
				}

				return Mono.just(new HazelcastSession(saved));
			});
	}

	@Override
	public Mono<Void> deleteById(String id) {
		return new MonoICompletableFuture<>(this.sessions.removeAsync(id))
			.then();
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

		/**
		 * Creates a new instance ensuring to mark all of the new attributes to be
		 * persisted in the next save operation.
		 */
		HazelcastSession() {
			this(new MapSession());
			this.isNew = true;
			flushImmediateIfNecessary().subscribe();
		}

		/**
		 * Creates a new instance from the provided {@link MapSession}.
		 * @param cached the {@link MapSession} that represents the persisted session that
		 * was retrieved. Cannot be {@code null}.
		 */
		HazelcastSession(MapSession cached) {
			Assert.notNull(cached, "MapSession cannot be null");
			this.delegate = cached;
			this.originalId = cached.getId();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.lastAccessedTimeChanged = true;
			flushImmediateIfNecessary().subscribe();
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
			flushImmediateIfNecessary().subscribe();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			return this.delegate.getAttribute(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
			this.delta.put(attributeName, attributeValue);
			flushImmediateIfNecessary().subscribe();
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
			this.delta.put(attributeName, null);
			flushImmediateIfNecessary().subscribe();
		}

		MapSession getDelegate() {
			return this.delegate;
		}

		boolean hasChanges() {
			return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged
					|| !this.delta.isEmpty());
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.lastAccessedTimeChanged = false;
			this.sessionIdChanged = false;
			this.maxInactiveIntervalChanged = false;
			this.delta.clear();
		}

		private Mono<Void> flushImmediateIfNecessary() {
			if (HazelcastReactiveSessionRepository.this.hazelcastFlushMode == HazelcastFlushMode.IMMEDIATE) {
				return HazelcastReactiveSessionRepository.this.save(this);
			}

			return Mono.empty();
		}

	}

	static final class MonoICompletableFuture<T> extends Mono<T>
			implements Fuseable, Scannable {

		final ICompletableFuture<? extends T> future;

		MonoICompletableFuture(ICompletableFuture<? extends T> future) {
			this.future = Objects.requireNonNull(future, "future");
		}

		@Override
		@SuppressWarnings("unchecked")
		public void subscribe(CoreSubscriber<? super T> actual) {
			Operators.MonoSubscriber<T, T>
					sds = new Operators.MonoSubscriber<>(actual);

			actual.onSubscribe(sds);

			if (sds.isCancelled()) {
				return;
			}

			future.andThen(new ExecutionCallback() {
				public void onResponse(Object v) {
					try {
						if (v != null) {
							sds.complete((T) v);
						}
						else {
							actual.onComplete();
						}
					}
					catch (Throwable e1) {
						Operators.onErrorDropped(e1, actual.currentContext());
						throw Exceptions.bubble(e1);
					}
				}

				public void onFailure(Throwable e) {
					try {
						if (e != null) {
							actual.onError(e);
						}
					}
					catch (Throwable e1) {
						Operators.onErrorDropped(e1, actual.currentContext());
						throw Exceptions.bubble(e1);
					}
				}
			});
		}

		@Override
		public Object scanUnsafe(Scannable.Attr key) {
			return null; //no particular key to be represented, still useful in hooks
		}
	}

}
