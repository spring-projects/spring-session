/*
 * Copyright 2002-2015 the original author or authors.
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

import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.MapSession;

/**
 * @author Rob Winch
 */
@RunWith(MockitoJUnitRunner.class)
public class RedisSessionExpirationPolicyTests {
	// Wed Apr 15 10:28:32 CDT 2015
	final static Long NOW = 1429111712346L;

	// Wed Apr 15 10:27:32 CDT 2015
	final static Long ONE_MINUTE_AGO = 1429111652346L;

	@Mock
	RedisOperations<Object,Object> sessionRedisOperations;
	@Mock
	BoundSetOperations<Object,Object> setOperations;
	@Mock
	BoundHashOperations<Object,Object, Object> hashOperations;
	@Mock
	BoundValueOperations<Object, Object> valueOperations;

	RedisSessionExpirationPolicy policy;

	private MapSession session;

	@Before
	public void setup() {
		RedisOperationsSessionRepository repository = new RedisOperationsSessionRepository(sessionRedisOperations);
		policy = new RedisSessionExpirationPolicy(sessionRedisOperations);
		session = new MapSession();
		session.setLastAccessedTime(1429116694665L);
		session.setId("12345");

		when(sessionRedisOperations.boundSetOps(anyString())).thenReturn(setOperations);
		when(sessionRedisOperations.boundHashOps(anyString())).thenReturn(hashOperations);
		when(sessionRedisOperations.boundValueOps(anyString())).thenReturn(valueOperations);
	}

	// gh-169
	@Test
	public void onExpirationUpdatedRemovesOriginalExpirationTimeRoundedUp() throws Exception {
		long originalExpirationTimeInMs = ONE_MINUTE_AGO;
		long originalRoundedToNextMinInMs = RedisSessionExpirationPolicy.roundUpToNextMinute(originalExpirationTimeInMs);
		String originalExpireKey = policy.getExpirationKey(originalRoundedToNextMinInMs);

		policy.onExpirationUpdated(originalExpirationTimeInMs, session);

		// verify the original is removed
		verify(sessionRedisOperations).boundSetOps(originalExpireKey);
		verify(setOperations).remove("expires:"+ session.getId());
	}

	@Test
	public void onExpirationUpdatedAddsExpirationTimeRoundedUp() throws Exception {
		long expirationTimeInMs = RedisSessionExpirationPolicy.expiresInMillis(session);
		long expirationRoundedUpInMs = RedisSessionExpirationPolicy.roundUpToNextMinute(expirationTimeInMs);
		String expectedExpireKey = policy.getExpirationKey(expirationRoundedUpInMs);

		policy.onExpirationUpdated(null, session);

		verify(sessionRedisOperations).boundSetOps(expectedExpireKey);
		verify(setOperations).add("expires:" + session.getId());
		verify(setOperations).expire(session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5), TimeUnit.SECONDS);
	}

	@Test
	public void onExpirationUpdatedSetExpireSession() throws Exception {
		String sessionKey = policy.getSessionKey(session.getId());

		policy.onExpirationUpdated(null, session);

		verify(sessionRedisOperations).boundHashOps(sessionKey);
		verify(hashOperations).expire(session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5), TimeUnit.SECONDS);
	}
}
