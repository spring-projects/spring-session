/*
 * Copyright 2014-2024 the original author or authors.
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

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Uses a sorted set to store the expiration times for sessions. The score of each entry
 * is the expiration time of the session (calculated via
 * {@link Session#getLastAccessedTime()} + {@link Session#getMaxInactiveInterval()}). The
 * value is the session id. Note that {@link #cleanupExpiredSessions()} only retrieves up
 * to 100 sessions at a time by default, use {@link #setCleanupCount(int)} to increase it
 * if needed.
 *
 * @author Marcus da Coregio
 * @since 3.4
 */
public class SortedSetRedisSessionExpirationStore implements RedisSessionExpirationStore {

	private final RedisOperations<String, Object> redisOps;

	private String namespace;

	private int cleanupCount = 100;

	private Clock clock = Clock.systemUTC();

	private String expirationsKey;

	public SortedSetRedisSessionExpirationStore(RedisOperations<String, Object> redisOps, String namespace) {
		Assert.notNull(redisOps, "redisOps cannot be null");
		this.redisOps = redisOps;
		setNamespace(namespace);
	}

	/**
	 * Save the session id associated with the expiration time into the sorted set.
	 * @param session the session to save
	 */
	@Override
	public void save(RedisIndexedSessionRepository.RedisSession session) {
		long expirationInMillis = getExpirationTime(session).toEpochMilli();
		this.redisOps.opsForZSet().add(this.expirationsKey, session.getId(), expirationInMillis);
	}

	/**
	 * Remove the session id from the sorted set.
	 * @param sessionId the session id
	 */
	@Override
	public void remove(String sessionId) {
		this.redisOps.opsForZSet().remove(this.expirationsKey, sessionId);
	}

	/**
	 * Retrieves the sessions that are expected to be expired and invoke
	 * {@link #touch(String)} on each of the session keys, resolved via
	 * {@link #getSessionKey(String)}.
	 */
	@Override
	public void cleanupExpiredSessions() {
		Set<Object> sessionIds = this.redisOps.opsForZSet()
			.reverseRangeByScore(this.expirationsKey, 0, this.clock.millis(), 0, this.cleanupCount);
		if (CollectionUtils.isEmpty(sessionIds)) {
			return;
		}
		for (Object sessionId : sessionIds) {
			String sessionKey = getSessionKey((String) sessionId);
			touch(sessionKey);
		}
	}

	private Instant getExpirationTime(RedisIndexedSessionRepository.RedisSession session) {
		return session.getLastAccessedTime().plus(session.getMaxInactiveInterval());
	}

	/**
	 * Checks if the session exists. By trying to access the session we only trigger a
	 * deletion if the TTL is expired. This is done to handle
	 * <a href="https://github.com/spring-projects/spring-session/issues/93">gh-93</a>
	 * @param sessionKey the key
	 */
	private void touch(String sessionKey) {
		this.redisOps.hasKey(sessionKey);
	}

	private String getSessionKey(String sessionId) {
		return this.namespace + ":sessions:" + sessionId;
	}

	/**
	 * Set the namespace for the keys.
	 * @param namespace the namespace
	 */
	public void setNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be null or empty");
		this.namespace = namespace;
		this.expirationsKey = this.namespace + ":sessions:expirations";
	}

	/**
	 * Configure the clock used when retrieving expired sessions for clean-up.
	 * @param clock the clock
	 */
	public void setClock(Clock clock) {
		Assert.notNull(clock, "clock cannot be null");
		this.clock = clock;
	}

	/**
	 * Configures how many sessions will be queried at a time to be cleaned up. Defaults
	 * to 100.
	 * @param cleanupCount how many sessions to be queried, must be bigger than 0.
	 */
	public void setCleanupCount(int cleanupCount) {
		Assert.state(cleanupCount > 0, "cleanupCount must be greater than 0");
		this.cleanupCount = cleanupCount;
	}

}
