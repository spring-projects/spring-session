/*
 * Copyright 2014-2023 the original author or authors.
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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.SessionRepository;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.util.Assert;

/**
 * A {@link SessionRepository} implementation that uses Spring Data's
 * {@link RedisOperations} to store sessions is Redis.
 * <p>
 * This implementation does not support publishing of session events.
 *
 * @author Vedran Pavic
 * @since 2.2.0
 */
public class RedisSessionRepository implements SessionRepository<RedisSessionRepository.RedisSession> {

	/**
	 * The default namespace for each key and channel in Redis used by Spring Session.
	 */
	public static final String DEFAULT_KEY_NAMESPACE = "spring:session";

	private final RedisOperations<String, Object> sessionRedisOperations;

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private String keyNamespace = DEFAULT_KEY_NAMESPACE + ":";

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	private BiFunction<String, Map<String, Object>, MapSession> redisSessionMapper = new RedisSessionMapper();

	/**
	 * Create a new {@link RedisSessionRepository} instance.
	 * @param sessionRedisOperations the {@link RedisOperations} to use for managing
	 * sessions
	 */
	public RedisSessionRepository(RedisOperations<String, Object> sessionRedisOperations) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations mut not be null");
		this.sessionRedisOperations = sessionRedisOperations;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 30 minutes.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the Redis key namespace.
	 * @param namespace the Redis key namespace
	 */
	public void setRedisKeyNamespace(String namespace) {
		Assert.hasText(namespace, "namespace must not be empty");
		this.keyNamespace = namespace.trim() + ":";
	}

	/**
	 * Set the flush mode.
	 * @param flushMode the flush mode
	 */
	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode must not be null");
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
	public RedisSession createSession() {
		MapSession cached = new MapSession(this.sessionIdGenerator);
		cached.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
		RedisSession session = new RedisSession(cached, true);
		session.flushIfRequired();
		return session;
	}

	@Override
	public void save(RedisSession session) {
		if (!session.isNew) {
			String key = getSessionKey(session.hasChangedSessionId() ? session.originalSessionId : session.getId());
			Boolean sessionExists = this.sessionRedisOperations.hasKey(key);
			if (sessionExists == null || !sessionExists) {
				throw new IllegalStateException("Session was invalidated");
			}
		}
		session.save();
	}

	@Override
	public RedisSession findById(String sessionId) {
		String key = getSessionKey(sessionId);
		Map<String, Object> entries = this.sessionRedisOperations.<String, Object>opsForHash().entries(key);
		if (entries.isEmpty()) {
			return null;
		}
		MapSession session = this.redisSessionMapper.apply(sessionId, entries);
		if (session == null || session.isExpired()) {
			deleteById(sessionId);
			return null;
		}
		return new RedisSession(session, false);
	}

	@Override
	public void deleteById(String sessionId) {
		String key = getSessionKey(sessionId);
		this.sessionRedisOperations.delete(key);
	}

	/**
	 * Returns the {@link RedisOperations} used for sessions.
	 * @return the {@link RedisOperations} used for sessions
	 */
	public RedisOperations<String, Object> getSessionRedisOperations() {
		return this.sessionRedisOperations;
	}

	private String getSessionKey(String sessionId) {
		return this.keyNamespace + "sessions:" + sessionId;
	}

	private static String getAttributeKey(String attributeName) {
		return RedisSessionMapper.ATTRIBUTE_PREFIX + attributeName;
	}

	/**
	 * Set the {@link SessionIdGenerator} to use to generate session ids.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @since 3.2
	 */
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

	/**
	 * Set the {@link BiFunction} used to map {@link MapSession} to a
	 * {@link ReactiveRedisSessionRepository.RedisSession}.
	 * @param redisSessionMapper the mapper to use, cannot be null
	 * @since 3.2
	 */
	public void setRedisSessionMapper(BiFunction<String, Map<String, Object>, MapSession> redisSessionMapper) {
		Assert.notNull(redisSessionMapper, "redisSessionMapper cannot be null");
		this.redisSessionMapper = redisSessionMapper;
	}

	/**
	 * An internal {@link Session} implementation used by this {@link SessionRepository}.
	 */
	final class RedisSession implements Session {

		private final MapSession cached;

		private final Map<String, Object> delta = new HashMap<>();

		private boolean isNew;

		private String originalSessionId;

		RedisSession(MapSession cached, boolean isNew) {
			this.cached = cached;
			this.isNew = isNew;
			this.originalSessionId = cached.getId();
			if (this.isNew) {
				this.delta.put(RedisSessionMapper.CREATION_TIME_KEY, cached.getCreationTime().toEpochMilli());
				this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY,
						(int) cached.getMaxInactiveInterval().getSeconds());
				this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, cached.getLastAccessedTime().toEpochMilli());
			}
			if (this.isNew || (RedisSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
				getAttributeNames().forEach((attributeName) -> this.delta.put(getAttributeKey(attributeName),
						cached.getAttribute(attributeName)));
			}
		}

		@Override
		public String getId() {
			return this.cached.getId();
		}

		@Override
		public String changeSessionId() {
			String newSessionId = RedisSessionRepository.this.sessionIdGenerator.generate();
			this.cached.setId(newSessionId);
			return newSessionId;
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			T attributeValue = this.cached.getAttribute(attributeName);
			if (attributeValue != null && RedisSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
				this.delta.put(getAttributeKey(attributeName), attributeValue);
			}
			return attributeValue;
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.cached.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.cached.setAttribute(attributeName, attributeValue);
			this.delta.put(getAttributeKey(attributeName), attributeValue);
			flushIfRequired();
		}

		@Override
		public void removeAttribute(String attributeName) {
			setAttribute(attributeName, null);
		}

		@Override
		public Instant getCreationTime() {
			return this.cached.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.cached.setLastAccessedTime(lastAccessedTime);
			this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
			flushIfRequired();
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) getMaxInactiveInterval().getSeconds());
			flushIfRequired();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.cached.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.cached.isExpired();
		}

		private void flushIfRequired() {
			if (RedisSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
				save();
			}
		}

		private boolean hasChangedSessionId() {
			return !getId().equals(this.originalSessionId);
		}

		private void save() {
			saveChangeSessionId();
			saveDelta();
			if (this.isNew) {
				this.isNew = false;
			}
		}

		private void saveChangeSessionId() {
			if (hasChangedSessionId()) {
				if (!this.isNew) {
					String originalSessionIdKey = getSessionKey(this.originalSessionId);
					String sessionIdKey = getSessionKey(getId());
					RedisSessionRepository.this.sessionRedisOperations.rename(originalSessionIdKey, sessionIdKey);
				}
				this.originalSessionId = getId();
			}
		}

		private void saveDelta() {
			if (this.delta.isEmpty()) {
				return;
			}
			String key = getSessionKey(getId());
			RedisSessionRepository.this.sessionRedisOperations.opsForHash().putAll(key, new HashMap<>(this.delta));
			RedisSessionRepository.this.sessionRedisOperations.expireAt(key,
					Instant.ofEpochMilli(getLastAccessedTime().toEpochMilli())
						.plusSeconds(getMaxInactiveInterval().getSeconds()));
			this.delta.clear();
		}

	}

}
