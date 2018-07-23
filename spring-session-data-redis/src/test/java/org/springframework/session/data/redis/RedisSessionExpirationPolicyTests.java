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
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.MapSession;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author Rob Winch
 */
@RunWith(MockitoJUnitRunner.class)
public class RedisSessionExpirationPolicyTests {
	// Wed Apr 15 10:28:32 CDT 2015
	static final Long NOW = 1429111712346L;

	// Wed Apr 15 10:27:32 CDT 2015
	static final Long ONE_MINUTE_AGO = 1429111652346L;

	@Mock
	RedisOperations<Object, Object> sessionRedisOperations;
	@Mock
	BoundSetOperations<Object, Object> setOperations;
	@Mock
	BoundHashOperations<Object, Object, Object> hashOperations;
	@Mock
	BoundValueOperations<Object, Object> valueOperations;

	RedisSessionExpirationPolicy policy;

	private MapSession session;

	@Before
	public void setup() {
		RedisOperationsSessionRepository repository = new RedisOperationsSessionRepository(
				this.sessionRedisOperations);
		this.policy = new RedisSessionExpirationPolicy(this.sessionRedisOperations,
				repository::getExpirationsKey, repository::getSessionKey);
		this.session = new MapSession();
		this.session.setLastAccessedTime(Instant.ofEpochMilli(1429116694675L));
		this.session.setId("12345");

		given(this.sessionRedisOperations.boundSetOps(anyString()))
				.willReturn(this.setOperations);
		given(this.sessionRedisOperations.boundHashOps(anyString()))
				.willReturn(this.hashOperations);
		given(this.sessionRedisOperations.boundValueOps(anyString()))
				.willReturn(this.valueOperations);
	}

	// gh-169
	@Test
	public void onExpirationUpdatedRemovesOriginalExpirationTimeRoundedUp()
			throws Exception {
		long originalExpirationTimeInMs = ONE_MINUTE_AGO;
		long originalRoundedToNextMinInMs = RedisSessionExpirationPolicy
				.roundUpToNextMinute(originalExpirationTimeInMs);
		String originalExpireKey = this.policy
				.getExpirationKey(originalRoundedToNextMinInMs);

		this.policy.onExpirationUpdated(originalExpirationTimeInMs, this.session);

		// verify the original is removed
		verify(this.sessionRedisOperations).boundSetOps(originalExpireKey);
		verify(this.setOperations).remove("expires:" + this.session.getId());
	}

	@Test
	public void onExpirationUpdatedDoNotSendDeleteWhenExpirationTimeDoesNotChange()
			throws Exception {
		long originalExpirationTimeInMs = RedisSessionExpirationPolicy
				.expiresInMillis(this.session) - 10;
		long originalRoundedToNextMinInMs = RedisSessionExpirationPolicy
				.roundUpToNextMinute(originalExpirationTimeInMs);
		String originalExpireKey = this.policy
				.getExpirationKey(originalRoundedToNextMinInMs);

		this.policy.onExpirationUpdated(originalExpirationTimeInMs, this.session);

		// verify the original is not removed
		verify(this.sessionRedisOperations).boundSetOps(originalExpireKey);
		verify(this.setOperations, never()).remove("expires:" + this.session.getId());
	}

	@Test
	public void onExpirationUpdatedAddsExpirationTimeRoundedUp() throws Exception {
		long expirationTimeInMs = RedisSessionExpirationPolicy
				.expiresInMillis(this.session);
		long expirationRoundedUpInMs = RedisSessionExpirationPolicy
				.roundUpToNextMinute(expirationTimeInMs);
		String expectedExpireKey = this.policy.getExpirationKey(expirationRoundedUpInMs);

		this.policy.onExpirationUpdated(null, this.session);

		verify(this.sessionRedisOperations).boundSetOps(expectedExpireKey);
		verify(this.setOperations).add("expires:" + this.session.getId());
		verify(this.setOperations).expire(
				this.session.getMaxInactiveInterval().plusMinutes(5).getSeconds(),
				TimeUnit.SECONDS);
	}

	@Test
	public void onExpirationUpdatedSetExpireSession() throws Exception {
		String sessionKey = this.policy.getSessionKey(this.session.getId());

		this.policy.onExpirationUpdated(null, this.session);

		verify(this.sessionRedisOperations).boundHashOps(sessionKey);
		verify(this.hashOperations).expire(
				this.session.getMaxInactiveInterval().plusMinutes(5).getSeconds(),
				TimeUnit.SECONDS);
	}

	@Test
	public void onExpirationUpdatedDeleteOnZero() throws Exception {
		String sessionKey = this.policy.getSessionKey("expires:" + this.session.getId());

		long originalExpirationTimeInMs = ONE_MINUTE_AGO;

		this.session.setMaxInactiveInterval(Duration.ZERO);

		this.policy.onExpirationUpdated(originalExpirationTimeInMs, this.session);

		// verify the original is removed
		verify(this.setOperations).remove("expires:" + this.session.getId());
		verify(this.setOperations).add("expires:" + this.session.getId());
		verify(this.sessionRedisOperations).delete(sessionKey);
		verify(this.setOperations).expire(
				this.session.getMaxInactiveInterval().plusMinutes(5).getSeconds(),
				TimeUnit.SECONDS);
	}

	@Test
	public void onExpirationUpdatedPersistOnNegativeExpiration() throws Exception {
		long originalExpirationTimeInMs = ONE_MINUTE_AGO;

		this.session.setMaxInactiveInterval(Duration.ofSeconds(-1));

		this.policy.onExpirationUpdated(originalExpirationTimeInMs, this.session);

		verify(this.setOperations).remove("expires:" + this.session.getId());
		verify(this.valueOperations).append("");
		verify(this.valueOperations).persist();
		verify(this.hashOperations).persist();
	}
}
