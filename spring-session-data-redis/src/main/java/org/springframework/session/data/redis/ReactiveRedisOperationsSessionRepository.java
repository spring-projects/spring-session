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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * A {@link ReactiveSessionRepository} that is implemented using Spring Data's
 * {@link ReactiveRedisOperations}.
 *
 * @author Vedran Pavic
 * @since 2.0
 */
public class ReactiveRedisOperationsSessionRepository implements
		ReactiveSessionRepository<ReactiveRedisOperationsSessionRepository.RedisSession> {

	/**
	 * The default namespace for each key and channel in Redis used by Spring Session.
	 */
	public static final String DEFAULT_NAMESPACE = "spring:session";

	/**
	 * The key in the Hash representing {@link Session#getCreationTime()}.
	 */
	static final String CREATION_TIME_KEY = "creationTime";

	/**
	 * The key in the Hash representing {@link Session#getLastAccessedTime()}.
	 */
	static final String LAST_ACCESSED_TIME_KEY = "lastAccessedTime";

	/**
	 * The key in the Hash representing {@link Session#getMaxInactiveInterval()} .
	 */
	static final String MAX_INACTIVE_INTERVAL_KEY = "maxInactiveInterval";

	/**
	 * The prefix of the key used for session attributes. The suffix is the name of
	 * the session attribute. For example, if the session contained an attribute named
	 * attributeName, then there would be an entry in the hash named
	 * sessionAttr:attributeName that mapped to its value.
	 */
	static final String ATTRIBUTE_PREFIX = "sessionAttr:";

	private final ReactiveRedisOperations<String, Object> sessionRedisOperations;

	/**
	 * The namespace for every key used by Spring Session in Redis.
	 */
	private String namespace = DEFAULT_NAMESPACE + ":";

	/**
	 * If non-null, this value is used to override the default value for
	 * {@link RedisSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private RedisFlushMode redisFlushMode = RedisFlushMode.ON_SAVE;

	public ReactiveRedisOperationsSessionRepository(
			ReactiveRedisOperations<String, Object> sessionRedisOperations) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations cannot be null");
		this.sessionRedisOperations = sessionRedisOperations;
	}

	public void setRedisKeyNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be null or empty");
		this.namespace = namespace.trim() + ":";
	}

	/**
	 * Sets the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * timeout. The default is 1800 (30 minutes).
	 *
	 * @param defaultMaxInactiveInterval the number of seconds that the {@link Session}
	 * should be kept alive between client requests.
	 */
	public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Sets the redis flush mode. Default flush mode is {@link RedisFlushMode#ON_SAVE}.
	 *
	 * @param redisFlushMode the new redis flush mode
	 */
	public void setRedisFlushMode(RedisFlushMode redisFlushMode) {
		Assert.notNull(redisFlushMode, "redisFlushMode cannot be null");
		this.redisFlushMode = redisFlushMode;
	}

	/**
	 * Returns the {@link ReactiveRedisOperations} used for sessions.
	 * @return the {@link ReactiveRedisOperations} used for sessions
	 * @since 2.1.0
	 */
	public ReactiveRedisOperations<String, Object> getSessionRedisOperations() {
		return this.sessionRedisOperations;
	}

	@Override
	public Mono<RedisSession> createSession() {
		return Mono.defer(() -> {
			RedisSession session = new RedisSession();

			if (this.defaultMaxInactiveInterval != null) {
				session.setMaxInactiveInterval(
						Duration.ofSeconds(this.defaultMaxInactiveInterval));
			}

			return Mono.just(session);
		});
	}

	@Override
	public Mono<Void> save(RedisSession session) {
		Mono<Void> result = session.saveChangeSessionId().and(session.saveDelta())
				.and((s) -> {
					session.isNew = false;
					s.onComplete();
				});
		if (session.isNew) {
			return result;
		}
		else {
			String sessionKey = getSessionKey(
					session.hasChangedSessionId() ? session.originalSessionId
							: session.getId());
			return this.sessionRedisOperations.hasKey(sessionKey)
					.flatMap((exists) -> exists ? result
							: Mono.error(new IllegalStateException(
									"Session was invalidated")));
		}
	}

	@Override
	public Mono<RedisSession> findById(String id) {
		String sessionKey = getSessionKey(id);

		// @formatter:off
		return this.sessionRedisOperations.opsForHash().entries(sessionKey)
				.collectMap((e) -> e.getKey().toString(), Map.Entry::getValue)
				.filter((map) -> !map.isEmpty())
				.map(new SessionMapper(id))
				.filter((session) -> !session.isExpired())
				.map(RedisSession::new)
				.switchIfEmpty(Mono.defer(() -> deleteById(id).then(Mono.empty())));
		// @formatter:on
	}

	@Override
	public Mono<Void> deleteById(String id) {
		String sessionKey = getSessionKey(id);

		return this.sessionRedisOperations.delete(sessionKey).then();
	}

	private static String getAttributeKey(String attributeName) {
		return ATTRIBUTE_PREFIX + attributeName;
	}

	private String getSessionKey(String sessionId) {
		return this.namespace + "sessions:" + sessionId;
	}

	/**
	 * A custom implementation of {@link Session} that uses a {@link MapSession} as the
	 * basis for its mapping. It keeps track of any attributes that have changed. When
	 * {@link RedisSession#saveDelta()} is invoked all the attributes that have been
	 * changed will be persisted.
	 */
	final class RedisSession implements Session {

		private final MapSession cached;

		private final Map<String, Object> delta = new HashMap<>();

		private boolean isNew;

		private String originalSessionId;

		/**
		 * Creates a new instance ensuring to mark all of the new attributes to be
		 * persisted in the next save operation.
		 */
		RedisSession() {
			this(new MapSession());
			this.delta.put(CREATION_TIME_KEY, getCreationTime().toEpochMilli());
			this.delta.put(MAX_INACTIVE_INTERVAL_KEY,
					(int) getMaxInactiveInterval().getSeconds());
			this.delta.put(LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
			this.isNew = true;
			this.flushImmediateIfNecessary();
		}

		/**
		 * Creates a new instance from the provided {@link MapSession}.
		 *
		 * @param mapSession the {@link MapSession} that represents the persisted session
		 * that was retrieved. Cannot be null.
		 */
		RedisSession(MapSession mapSession) {
			Assert.notNull(mapSession, "mapSession cannot be null");
			this.cached = mapSession;
			this.originalSessionId = mapSession.getId();
		}

		@Override
		public String getId() {
			return this.cached.getId();
		}

		@Override
		public String changeSessionId() {
			return this.cached.changeSessionId();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			return this.cached.getAttribute(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.cached.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.cached.setAttribute(attributeName, attributeValue);
			putAndFlush(getAttributeKey(attributeName), attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.cached.removeAttribute(attributeName);
			putAndFlush(getAttributeKey(attributeName), null);
		}

		@Override
		public Instant getCreationTime() {
			return this.cached.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.cached.setLastAccessedTime(lastAccessedTime);
			putAndFlush(LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			putAndFlush(MAX_INACTIVE_INTERVAL_KEY,
					(int) getMaxInactiveInterval().getSeconds());
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.cached.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.cached.isExpired();
		}

		private boolean hasChangedSessionId() {
			return !getId().equals(this.originalSessionId);
		}

		private void flushImmediateIfNecessary() {
			if (ReactiveRedisOperationsSessionRepository.this.redisFlushMode == RedisFlushMode.IMMEDIATE) {
				saveDelta();
			}
		}

		private void putAndFlush(String a, Object v) {
			this.delta.put(a, v);
			flushImmediateIfNecessary();
		}

		private Mono<Void> saveDelta() {
			if (this.delta.isEmpty()) {
				return Mono.empty();
			}

			String sessionKey = getSessionKey(getId());
			Mono<Boolean> update = ReactiveRedisOperationsSessionRepository.this.sessionRedisOperations
					.opsForHash().putAll(sessionKey, this.delta);
			Mono<Boolean> setTtl = ReactiveRedisOperationsSessionRepository.this.sessionRedisOperations
					.expire(sessionKey, getMaxInactiveInterval());

			return update.and(setTtl).and((s) -> {
				this.delta.clear();
				s.onComplete();
			}).then();
		}

		private Mono<Void> saveChangeSessionId() {
			if (!hasChangedSessionId()) {
				return Mono.empty();
			}

			String sessionId = getId();

			Publisher<Void> replaceSessionId = (s) -> {
				this.originalSessionId = sessionId;
				s.onComplete();
			};

			if (this.isNew) {
				return Mono.from(replaceSessionId);
			}
			else {
				String originalSessionKey = getSessionKey(this.originalSessionId);
				String sessionKey = getSessionKey(sessionId);

				return ReactiveRedisOperationsSessionRepository.this.sessionRedisOperations
						.rename(originalSessionKey, sessionKey).and(replaceSessionId);
			}
		}

	}

	private static final class SessionMapper
			implements Function<Map<String, Object>, MapSession> {

		private final String id;

		private SessionMapper(String id) {
			this.id = id;
		}

		@Override
		public MapSession apply(Map<String, Object> map) {
			MapSession session = new MapSession(this.id);

			session.setCreationTime(
					Instant.ofEpochMilli((long) map.get(CREATION_TIME_KEY)));
			session.setLastAccessedTime(
					Instant.ofEpochMilli((long) map.get(LAST_ACCESSED_TIME_KEY)));
			session.setMaxInactiveInterval(
					Duration.ofSeconds((int) map.get(MAX_INACTIVE_INTERVAL_KEY)));

			map.forEach((name, value) -> {
				if (name.startsWith(ATTRIBUTE_PREFIX)) {
					session.setAttribute(name.substring(ATTRIBUTE_PREFIX.length()),
							value);
				}
			});

			return session;
		}

	}

}
