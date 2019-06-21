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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
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
public class SimpleRedisOperationsSessionRepository
		implements SessionRepository<SimpleRedisOperationsSessionRepository.RedisSession> {

	private static final String DEFAULT_KEY_NAMESPACE = "spring:session:";

	private final RedisOperations<String, Object> sessionRedisOperations;

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private String keyNamespace = DEFAULT_KEY_NAMESPACE;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	/**
	 * Create a new {@link SimpleRedisOperationsSessionRepository} instance.
	 * @param sessionRedisOperations the {@link RedisOperations} to use for managing
	 * sessions
	 */
	public SimpleRedisOperationsSessionRepository(RedisOperations<String, Object> sessionRedisOperations) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations mut not be null");
		this.sessionRedisOperations = sessionRedisOperations;
	}

	/**
	 * Set the default maxInactiveInterval.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the key namespace.
	 * @param keyNamespace the key namespace
	 */
	public void setKeyNamespace(String keyNamespace) {
		Assert.hasText(keyNamespace, "keyNamespace must not be empty");
		this.keyNamespace = keyNamespace;
	}

	/**
	 * Set the flush mode.
	 * @param flushMode the flush mode
	 */
	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode must not be null");
		this.flushMode = flushMode;
	}

	@Override
	public RedisSession createSession() {
		RedisSession session = new RedisSession(this.defaultMaxInactiveInterval);
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
		MapSession session = new RedisSessionMapper(sessionId).apply(entries);
		if (session.isExpired()) {
			deleteById(sessionId);
			return null;
		}
		return new RedisSession(session);
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

	/**
	 * An internal {@link Session} implementation used by this {@link SessionRepository}.
	 */
	final class RedisSession implements Session {

		private final MapSession cached;

		private final Map<String, Object> delta = new HashMap<>();

		private boolean isNew;

		private String originalSessionId;

		RedisSession(Duration maxInactiveInterval) {
			this(new MapSession());
			this.cached.setMaxInactiveInterval(maxInactiveInterval);
			this.delta.put(RedisSessionMapper.CREATION_TIME_KEY, getCreationTime().toEpochMilli());
			this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) getMaxInactiveInterval().getSeconds());
			this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
			this.isNew = true;
		}

		RedisSession(MapSession cached) {
			this.cached = cached;
			this.originalSessionId = cached.getId();
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
			putAttribute(RedisSessionMapper.ATTRIBUTE_PREFIX + attributeName, attributeValue);
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
			putAttribute(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			putAttribute(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) getMaxInactiveInterval().getSeconds());
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
			if (SimpleRedisOperationsSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
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
					SimpleRedisOperationsSessionRepository.this.sessionRedisOperations.rename(originalSessionIdKey,
							sessionIdKey);
				}
				this.originalSessionId = getId();
			}
		}

		private void saveDelta() {
			if (this.delta.isEmpty()) {
				return;
			}
			String key = getSessionKey(getId());
			SimpleRedisOperationsSessionRepository.this.sessionRedisOperations.opsForHash().putAll(key,
					new HashMap<>(this.delta));
			SimpleRedisOperationsSessionRepository.this.sessionRedisOperations.expireAt(key,
					Date.from(Instant.ofEpochMilli(getLastAccessedTime().toEpochMilli())
							.plusSeconds(getMaxInactiveInterval().getSeconds())));
			this.delta.clear();
		}

		private void putAttribute(String name, Object value) {
			this.delta.put(name, value);
			flushIfRequired();
		}

	}

}
