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

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.CREATION_TIME_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.LAST_ACCESSED_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.MAX_INACTIVE_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.getKey;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.getSessionAttrNameKey;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession;


@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"unchecked","rawtypes"})
public class RedisOperationsSessionRepositoryTests {
	@Mock
	RedisConnectionFactory factory;
	@Mock
	RedisConnection connection;
	@Mock
	RedisOperations<Object,Object> redisOperations;
	@Mock
	BoundValueOperations<Object, Object> boundValueOperations;
	@Mock
	BoundHashOperations<Object, Object, Object> boundHashOperations;
	@Mock
	BoundSetOperations<Object, Object> boundSetOperations;
	@Captor
	ArgumentCaptor<Map<String,Object>> delta;

	private MapSession cached;

	private RedisOperationsSessionRepository redisRepository;

	@Before
	public void setup() {
		this.redisRepository = new RedisOperationsSessionRepository(redisOperations);

		cached = new MapSession();
		cached.setId("session-id");
		cached.setCreationTime(1404360000000L);
		cached.setLastAccessedTime(1404360000000L);
	}

	@Test(expected=IllegalArgumentException.class)
	public void constructorNullConnectionFactory() {
		new RedisOperationsSessionRepository((RedisConnectionFactory)null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void setApplicationEventPublisherNull() {
		redisRepository.setApplicationEventPublisher(null);
	}

	// gh-61
	@Test
	public void constructorConnectionFactory() {
		redisRepository = new RedisOperationsSessionRepository(factory);
		RedisSession session = redisRepository.createSession();

		when(factory.getConnection()).thenReturn(connection);

		redisRepository.save(session);
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() throws Exception {
		ExpiringSession session = redisRepository.createSession();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() throws Exception {
		int interval = 1;
		redisRepository.setDefaultMaxInactiveInterval(interval);
		ExpiringSession session = redisRepository.createSession();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(interval);
	}

	@Test
	public void saveNewSession() {
		RedisSession session = redisRepository.createSession();
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		Map<String,Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta.get(CREATION_TIME_ATTR);
		assertThat(creationTime).isEqualTo(session.getCreationTime());
		assertThat(delta.get(MAX_INACTIVE_ATTR)).isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		assertThat(delta.get(LAST_ACCESSED_ATTR)).isEqualTo(session.getCreationTime());
	}

	@Test
	public void saveJavadocSummary() {
		RedisSession session = redisRepository.createSession();

		String sessionKey = "spring:session:sessions:" + session.getId();
		String backgroundExpireKey = "spring:session:expirations:" + RedisSessionExpirationPolicy.roundUpToNextMinute(RedisSessionExpirationPolicy.expiresInMillis(session));
		String destroyedTriggerKey = "spring:session:sessions:expires:" + session.getId();

		when(redisOperations.boundHashOps(sessionKey)).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(backgroundExpireKey)).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(destroyedTriggerKey)).thenReturn(boundValueOperations);

		redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since getSession checks if it is expired
		long fiveMinutesAfterExpires = session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5);
		verify(boundHashOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(boundSetOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(boundSetOperations).add("expires:" + session.getId());
		verify(boundValueOperations).expire(1800L, TimeUnit.SECONDS);
		verify(boundValueOperations).append("");
	}

	@Test
	public void saveJavadoc() {
		RedisSession session = redisRepository.new RedisSession(cached);

		when(redisOperations.boundHashOps("spring:session:sessions:session-id")).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps("spring:session:expirations:1404361860000")).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps("spring:session:sessions:expires:session-id")).thenReturn(boundValueOperations);

		redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since getSession checks if it is expired
		verify(boundHashOperations).expire(session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5), TimeUnit.SECONDS);
	}

	@Test
	public void saveLastAccessChanged() {
		RedisSession session = redisRepository.new RedisSession(new MapSession(cached));
		session.setLastAccessedTime(12345678L);
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(LAST_ACCESSED_ATTR, session.getLastAccessedTime()));
	}

	@Test
	public void saveSetAttribute() {
		String attrName = "attrName";
		RedisSession session = redisRepository.new RedisSession(new MapSession());
		session.setAttribute(attrName, "attrValue");
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	public void saveRemoveAttribute() {
		String attrName = "attrName";
		RedisSession session = redisRepository.new RedisSession(new MapSession());
		session.removeAttribute(attrName);
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(getSessionAttrNameKey(attrName), null));
	}

	@Test
	public void redisSessionGetAttributes() {
		String attrName = "attrName";
		RedisSession session = redisRepository.new RedisSession();
		assertThat(session.getAttributeNames()).isEmpty();
		session.setAttribute(attrName, "attrValue");
		assertThat(session.getAttributeNames()).containsOnly(attrName);
		session.removeAttribute(attrName);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void delete() {
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
		expected.setAttribute(attrName, "attrValue");
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);
		Map map = map(
				getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
				CREATION_TIME_ATTR, expected.getCreationTime(),
				MAX_INACTIVE_ATTR, expected.getMaxInactiveIntervalInSeconds(),
				LAST_ACCESSED_ATTR, expected.getLastAccessedTime());
		when(boundHashOperations.entries()).thenReturn(map);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

		String id = expected.getId();
		redisRepository.delete(id);

		assertThat(getDelta().get(MAX_INACTIVE_ATTR)).isEqualTo(0);
		verify(redisOperations).delete(getKey("expires:"+id));
	}

	@Test
	public void deleteNullSession() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		String id = "abc";
		redisRepository.delete(id);
		verify(redisOperations,times(0)).delete(anyString());
		verify(redisOperations,times(0)).delete(anyString());
	}

	@Test
	public void getSessionNotFound() {
		String id = "abc";
		when(redisOperations.boundHashOps(getKey(id))).thenReturn(boundHashOperations);
		when(boundHashOperations.entries()).thenReturn(map());

		assertThat(redisRepository.getSession(id)).isNull();
	}

	@Test
	public void getSessionFound() {
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
		expected.setAttribute(attrName, "attrValue");
		when(redisOperations.boundHashOps(getKey(expected.getId()))).thenReturn(boundHashOperations);
		Map map = map(
				getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
				CREATION_TIME_ATTR, expected.getCreationTime(),
				MAX_INACTIVE_ATTR, expected.getMaxInactiveIntervalInSeconds(),
				LAST_ACCESSED_ATTR, expected.getLastAccessedTime());
		when(boundHashOperations.entries()).thenReturn(map);

		long now = System.currentTimeMillis();
		RedisSession session = redisRepository.getSession(expected.getId());
		assertThat(session.getId()).isEqualTo(expected.getId());
		assertThat(session.getAttributeNames()).isEqualTo(expected.getAttributeNames());
		assertThat(session.getAttribute(attrName)).isEqualTo(expected.getAttribute(attrName));
		assertThat(session.getCreationTime()).isEqualTo(expected.getCreationTime());
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expected.getMaxInactiveIntervalInSeconds());
		assertThat(session.getLastAccessedTime()).isGreaterThanOrEqualTo(now);

	}

	@Test
	public void getSessionExpired() {
		String expiredId = "expired-id";
		when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
		Map map = map(
				MAX_INACTIVE_ATTR, 1,
				LAST_ACCESSED_ATTR, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		when(boundHashOperations.entries()).thenReturn(map);

		assertThat(redisRepository.getSession(expiredId)).isNull();
	}

	@Test
	public void findByPrincipalNameExpired() {
		String expiredId = "expired-id";
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(boundSetOperations.members()).thenReturn(Collections.<Object>singleton(expiredId));
		when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
		Map map = map(
				MAX_INACTIVE_ATTR, 1,
				LAST_ACCESSED_ATTR, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		when(boundHashOperations.entries()).thenReturn(map);

		assertThat(redisRepository.findByPrincipalName("principal")).isEmpty();
	}

	@Test
	public void findByPrincipalName() {
		long lastAccessed = System.currentTimeMillis() - 10;
		long createdTime = lastAccessed - 10;
		int maxInactive = 3600;
		String sessionId = "some-id";
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(boundSetOperations.members()).thenReturn(Collections.<Object>singleton(sessionId));
		when(redisOperations.boundHashOps(getKey(sessionId))).thenReturn(boundHashOperations);
		Map map = map(
				CREATION_TIME_ATTR, createdTime,
				MAX_INACTIVE_ATTR, maxInactive,
				LAST_ACCESSED_ATTR, lastAccessed);
		when(boundHashOperations.entries()).thenReturn(map);

		Map<String, RedisSession> sessionIdToSessions = redisRepository.findByPrincipalName("principal");

		assertThat(sessionIdToSessions).hasSize(1);
		RedisSession session = sessionIdToSessions.get(sessionId);
		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo(sessionId);
		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessed);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(maxInactive);
		assertThat(session.getCreationTime()).isEqualTo(createdTime);
	}

	@Test
	public void cleanupExpiredSessions() {
		String expiredId = "expired-id";
		when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

		Set<Object> expiredIds = new HashSet<Object>(Arrays.asList("expired-key1","expired-key2"));
		when(boundSetOperations.members()).thenReturn(expiredIds);

		redisRepository.cleanupExpiredSessions();

		for(Object id : expiredIds) {
			String expiredKey = RedisOperationsSessionRepository.BOUNDED_HASH_KEY_PREFIX + id;
			// https://github.com/spring-projects/spring-session/issues/93
			verify(redisOperations).hasKey(expiredKey);
		}
	}

	private Map map(Object...objects) {
		Map<String,Object> result = new HashMap<String,Object>();
		if(objects == null) {
			return result;
		}
		for(int i = 0; i < objects.length; i += 2) {
			result.put((String)objects[i], objects[i+1]);
		}
		return result;
	}

	private Map<String,Object> getDelta() {
		verify(boundHashOperations).putAll(delta.capture());
		return delta.getValue();
	}
}