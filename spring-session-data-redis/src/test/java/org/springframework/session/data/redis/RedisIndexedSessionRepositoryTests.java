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

package org.springframework.session.data.redis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;
import org.springframework.session.events.AbstractSessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RedisIndexedSessionRepositoryTests {

	@Mock
	private RedisOperations<Object, Object> redisOperations;

	@Mock
	private BoundValueOperations<Object, Object> boundValueOperations;

	@Mock
	private BoundHashOperations<Object, Object, Object> boundHashOperations;

	@Mock
	private BoundSetOperations<Object, Object> boundSetOperations;

	@Mock
	private ApplicationEventPublisher publisher;

	@Mock
	private RedisSerializer<Object> defaultSerializer;

	@Captor
	private ArgumentCaptor<AbstractSessionEvent> event;

	@Captor
	private ArgumentCaptor<Map<String, Object>> delta;

	private MapSession cached;

	private RedisIndexedSessionRepository redisRepository;

	@BeforeEach
	void setup() {
		this.redisRepository = new RedisIndexedSessionRepository(this.redisOperations);
		this.redisRepository.setDefaultSerializer(this.defaultSerializer);

		this.cached = new MapSession();
		this.cached.setId("session-id");
		this.cached.setCreationTime(Instant.ofEpochMilli(1404360000000L));
		this.cached.setLastAccessedTime(Instant.ofEpochMilli(1404360000000L));
	}

	@Test
	void setApplicationEventPublisherNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.redisRepository.setApplicationEventPublisher(null))
				.withMessage("applicationEventPublisher cannot be null");
	}

	@Test
	void changeSessionIdWhenNotSaved() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		RedisSession createSession = this.redisRepository.createSession();
		String originalId = createSession.getId();
		String changeSessionId = createSession.changeSessionId();
		this.redisRepository.save(createSession);

		verify(this.redisOperations, never()).rename(anyString(), anyString());

		assertThat(originalId).isNotEqualTo(changeSessionId);
		assertThat(createSession.getId()).isEqualTo(createSession.getId());
	}

	@Test
	void changeSessionIdWhenSaved() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		RedisSession session = this.redisRepository.new RedisSession(this.cached, false);
		session.setLastAccessedTime(session.getLastAccessedTime());
		String originalId = session.getId();
		String changeSessionId = session.changeSessionId();
		this.redisRepository.save(session);

		verify(this.redisOperations, times(2)).rename(anyString(), anyString());

		assertThat(originalId).isNotEqualTo(changeSessionId);
		assertThat(session.getId()).isEqualTo(session.getId());
	}

	@Test
	void createSessionDefaultMaxInactiveInterval() {
		Session session = this.redisRepository.createSession();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(new MapSession().getMaxInactiveInterval());
	}

	@Test
	void createSessionCustomMaxInactiveInterval() {
		int interval = 1;
		this.redisRepository.setDefaultMaxInactiveInterval(interval);
		Session session = this.redisRepository.createSession();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(interval));
	}

	@Test
	void saveNewSession() {
		RedisSession session = this.redisRepository.createSession();
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		Map<String, Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta.get(RedisSessionMapper.CREATION_TIME_KEY);
		assertThat(creationTime).isEqualTo(session.getCreationTime().toEpochMilli());
		assertThat(delta.get(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY))
				.isEqualTo((int) Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS).getSeconds());
		assertThat(delta.get(RedisSessionMapper.LAST_ACCESSED_TIME_KEY))
				.isEqualTo(session.getCreationTime().toEpochMilli());
	}

	// gh-467
	@Test
	void saveSessionNothingChanged() {
		RedisSession session = this.redisRepository.new RedisSession(this.cached, false);

		this.redisRepository.save(session);

		verifyNoMoreInteractions(this.redisOperations);
	}

	@Test
	void saveJavadocSummary() {
		RedisSession session = this.redisRepository.createSession();

		String sessionKey = "spring:session:sessions:" + session.getId();
		String backgroundExpireKey = "spring:session:expirations:" + RedisSessionExpirationPolicy
				.roundUpToNextMinute(RedisSessionExpirationPolicy.expiresInMillis(session));
		String destroyedTriggerKey = "spring:session:sessions:expires:" + session.getId();

		given(this.redisOperations.boundHashOps(sessionKey)).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(backgroundExpireKey)).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(destroyedTriggerKey)).willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data
		// can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since
		// findById checks if it is expired
		long fiveMinutesAfterExpires = session.getMaxInactiveInterval().plusMinutes(5).getSeconds();
		verify(this.boundHashOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(this.boundSetOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(this.boundSetOperations).add("expires:" + session.getId());
		verify(this.boundValueOperations).expire(1800L, TimeUnit.SECONDS);
		verify(this.boundValueOperations).append("");
	}

	@Test
	void saveJavadoc() {
		RedisSession session = this.redisRepository.new RedisSession(this.cached, false);
		session.setLastAccessedTime(session.getLastAccessedTime());

		given(this.redisOperations.boundHashOps("spring:session:sessions:session-id"))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps("spring:session:expirations:1404361860000"))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps("spring:session:sessions:expires:session-id"))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data
		// can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since
		// findById checks if it is expired
		verify(this.boundHashOperations).expire(session.getMaxInactiveInterval().plusMinutes(5).getSeconds(),
				TimeUnit.SECONDS);
	}

	@Test
	void saveLastAccessChanged() {
		RedisSession session = this.redisRepository.new RedisSession(this.cached, false);
		session.setLastAccessedTime(Instant.ofEpochMilli(12345678L));
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(
				map(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, session.getLastAccessedTime().toEpochMilli()));
	}

	@Test
	void saveSetAttribute() {
		String attrName = "attrName";
		RedisSession session = this.redisRepository.new RedisSession(new MapSession(), false);
		session.setAttribute(attrName, "attrValue");
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(
				map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	void saveRemoveAttribute() {
		String attrName = "attrName";
		RedisSession session = this.redisRepository.new RedisSession(new MapSession(), false);
		session.removeAttribute(attrName);
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), null));
	}

	@Test
	void saveExpired() {
		RedisSession session = this.redisRepository.new RedisSession(new MapSession(), false);
		session.setMaxInactiveInterval(Duration.ZERO);
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);

		this.redisRepository.save(session);

		String id = session.getId();
		verify(this.redisOperations, atLeastOnce()).delete(getKey("expires:" + id));
		verify(this.redisOperations, never()).boundValueOps(getKey("expires:" + id));
	}

	@Test
	void redisSessionGetAttributes() {
		String attrName = "attrName";
		RedisSession session = this.redisRepository.createSession();
		assertThat(session.getAttributeNames()).isEmpty();
		session.setAttribute(attrName, "attrValue");
		assertThat(session.getAttributeNames()).containsOnly(attrName);
		session.removeAttribute(attrName);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void delete() {
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(Instant.now().minusSeconds(60));
		expected.setAttribute(attrName, "attrValue");
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		Map map = map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
				RedisSessionMapper.CREATION_TIME_KEY, expected.getCreationTime().toEpochMilli(),
				RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) expected.getMaxInactiveInterval().getSeconds(),
				RedisSessionMapper.LAST_ACCESSED_TIME_KEY, expected.getLastAccessedTime().toEpochMilli());
		given(this.boundHashOperations.entries()).willReturn(map);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);

		String id = expected.getId();
		this.redisRepository.deleteById(id);

		assertThat(getDelta().get(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY)).isEqualTo(0);
		verify(this.redisOperations, atLeastOnce()).delete(getKey("expires:" + id));
		verify(this.redisOperations, never()).boundValueOps(getKey("expires:" + id));
	}

	@Test
	void deleteNullSession() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);

		String id = "abc";
		this.redisRepository.deleteById(id);
		verify(this.redisOperations, times(0)).delete(anyString());
		verify(this.redisOperations, times(0)).delete(anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void getSessionNotFound() {
		String id = "abc";
		given(this.redisOperations.boundHashOps(getKey(id))).willReturn(this.boundHashOperations);
		given(this.boundHashOperations.entries()).willReturn(map());

		assertThat(this.redisRepository.findById(id)).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void getSessionFound() {
		String attribute1 = "attribute1";
		String attribute2 = "attribute2";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(Instant.now().minusSeconds(60));
		expected.setAttribute(attribute1, "test");
		expected.setAttribute(attribute2, null);
		given(this.redisOperations.boundHashOps(getKey(expected.getId()))).willReturn(this.boundHashOperations);
		Map map = map(RedisIndexedSessionRepository.getSessionAttrNameKey(attribute1),
				expected.getAttribute(attribute1), RedisIndexedSessionRepository.getSessionAttrNameKey(attribute2),
				expected.getAttribute(attribute2), RedisSessionMapper.CREATION_TIME_KEY,
				expected.getCreationTime().toEpochMilli(), RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY,
				(int) expected.getMaxInactiveInterval().getSeconds(), RedisSessionMapper.LAST_ACCESSED_TIME_KEY,
				expected.getLastAccessedTime().toEpochMilli());
		given(this.boundHashOperations.entries()).willReturn(map);

		RedisSession session = this.redisRepository.findById(expected.getId());
		assertThat(session.getId()).isEqualTo(expected.getId());
		assertThat(session.getAttributeNames()).isEqualTo(expected.getAttributeNames());
		assertThat(session.<String>getAttribute(attribute1)).isEqualTo(expected.getAttribute(attribute1));
		assertThat(session.<String>getAttribute(attribute2)).isEqualTo(expected.getAttribute(attribute2));
		assertThat(session.getCreationTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(expected.getCreationTime().truncatedTo(ChronoUnit.MILLIS));
		assertThat(session.getMaxInactiveInterval()).isEqualTo(expected.getMaxInactiveInterval());
		assertThat(session.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(expected.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getSessionExpired() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundHashOps(getKey(expiredId))).willReturn(this.boundHashOperations);
		Map map = map(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1, RedisSessionMapper.LAST_ACCESSED_TIME_KEY,
				Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());
		given(this.boundHashOperations.entries()).willReturn(map);

		assertThat(this.redisRepository.findById(expiredId)).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void findByPrincipalNameExpired() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.boundSetOperations.members()).willReturn(Collections.singleton(expiredId));
		given(this.redisOperations.boundHashOps(getKey(expiredId))).willReturn(this.boundHashOperations);
		Map map = map(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1, RedisSessionMapper.LAST_ACCESSED_TIME_KEY,
				Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());
		given(this.boundHashOperations.entries()).willReturn(map);

		assertThat(this.redisRepository
				.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "principal"))
						.isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void findByPrincipalName() {
		Instant lastAccessed = Instant.now().minusMillis(10);
		Instant createdTime = lastAccessed.minusMillis(10);
		Duration maxInactive = Duration.ofHours(1);
		String sessionId = "some-id";
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.boundSetOperations.members()).willReturn(Collections.singleton(sessionId));
		given(this.redisOperations.boundHashOps(getKey(sessionId))).willReturn(this.boundHashOperations);
		Map map = map(RedisSessionMapper.CREATION_TIME_KEY, createdTime.toEpochMilli(),
				RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) maxInactive.getSeconds(),
				RedisSessionMapper.LAST_ACCESSED_TIME_KEY, lastAccessed.toEpochMilli());
		given(this.boundHashOperations.entries()).willReturn(map);

		Map<String, RedisSession> sessionIdToSessions = this.redisRepository
				.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "principal");

		assertThat(sessionIdToSessions).hasSize(1);
		RedisSession session = sessionIdToSessions.get(sessionId);
		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo(sessionId);
		assertThat(session.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(lastAccessed.truncatedTo(ChronoUnit.MILLIS));
		assertThat(session.getMaxInactiveInterval()).isEqualTo(maxInactive);
		assertThat(session.getCreationTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(createdTime.truncatedTo(ChronoUnit.MILLIS));
	}

	@Test
	void cleanupExpiredSessions() {
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);

		Set<Object> expiredIds = new HashSet<>(Arrays.asList("expired-key1", "expired-key2"));
		given(this.boundSetOperations.members()).willReturn(expiredIds);

		this.redisRepository.cleanupExpiredSessions();

		for (Object id : expiredIds) {
			String expiredKey = "spring:session:sessions:" + id;
			// https://github.com/spring-projects/spring-session/issues/93
			verify(this.redisOperations).hasKey(expiredKey);
		}
	}

	@Test
	void onMessageCreated() {
		MapSession session = this.cached;
		byte[] pattern = "".getBytes(StandardCharsets.UTF_8);
		String channel = "spring:session:event:0:created:" + session.getId();
		JdkSerializationRedisSerializer defaultSerailizer = new JdkSerializationRedisSerializer();
		this.redisRepository.setDefaultSerializer(defaultSerailizer);
		byte[] body = defaultSerailizer.serialize(new HashMap());
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body);

		this.redisRepository.setApplicationEventPublisher(this.publisher);

		this.redisRepository.onMessage(message, pattern);

		verify(this.publisher).publishEvent(this.event.capture());
		assertThat(this.event.getValue().getSessionId()).isEqualTo(session.getId());
	}

	@Test // gh-309
	void onMessageCreatedCustomSerializer() {
		MapSession session = this.cached;
		byte[] pattern = "".getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[0];
		String channel = "spring:session:event:0:created:" + session.getId();
		given(this.defaultSerializer.deserialize(body)).willReturn(new HashMap<String, Object>());
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body);
		this.redisRepository.setApplicationEventPublisher(this.publisher);

		this.redisRepository.onMessage(message, pattern);

		verify(this.publisher).publishEvent(this.event.capture());
		assertThat(this.event.getValue().getSessionId()).isEqualTo(session.getId());
		verify(this.defaultSerializer).deserialize(body);
	}

	@Test
	@SuppressWarnings("unchecked")
	void onMessageDeletedSessionFound() {
		String deletedId = "deleted-id";
		given(this.redisOperations.boundHashOps(getKey(deletedId))).willReturn(this.boundHashOperations);
		Map map = map(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 0, RedisSessionMapper.LAST_ACCESSED_TIME_KEY,
				System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		given(this.boundHashOperations.entries()).willReturn(map);

		String channel = "__keyevent@0__:del";
		String body = "spring:session:sessions:expires:" + deletedId;
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));

		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		verify(this.redisOperations).boundHashOps(eq(getKey(deletedId)));
		verify(this.boundHashOperations).entries();
		verify(this.publisher).publishEvent(this.event.capture());
		assertThat(this.event.getValue().getSessionId()).isEqualTo(deletedId);
		verifyNoMoreInteractions(this.defaultSerializer);
		verifyNoMoreInteractions(this.publisher);
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	void onMessageDeletedSessionNotFound() {
		String deletedId = "deleted-id";
		given(this.redisOperations.boundHashOps(getKey(deletedId))).willReturn(this.boundHashOperations);
		given(this.boundHashOperations.entries()).willReturn(map());

		String channel = "__keyevent@0__:del";
		String body = "spring:session:sessions:expires:" + deletedId;
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));

		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		verify(this.redisOperations).boundHashOps(eq(getKey(deletedId)));
		verify(this.boundHashOperations).entries();
		verifyNoMoreInteractions(this.defaultSerializer);
		verifyNoMoreInteractions(this.publisher);
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	void onMessageExpiredSessionFound() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundHashOps(getKey(expiredId))).willReturn(this.boundHashOperations);
		Map map = map(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1, RedisSessionMapper.LAST_ACCESSED_TIME_KEY,
				System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		given(this.boundHashOperations.entries()).willReturn(map);

		String channel = "__keyevent@0__:expired";
		String body = "spring:session:sessions:expires:" + expiredId;
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));

		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		verify(this.redisOperations).boundHashOps(eq(getKey(expiredId)));
		verify(this.boundHashOperations).entries();
		verify(this.publisher).publishEvent(this.event.capture());
		assertThat(this.event.getValue().getSessionId()).isEqualTo(expiredId);
		verifyNoMoreInteractions(this.defaultSerializer);
		verifyNoMoreInteractions(this.publisher);
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	void onMessageExpiredSessionNotFound() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundHashOps(getKey(expiredId))).willReturn(this.boundHashOperations);
		given(this.boundHashOperations.entries()).willReturn(map());

		String channel = "__keyevent@0__:expired";
		String body = "spring:session:sessions:expires:" + expiredId;
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));

		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		verify(this.redisOperations).boundHashOps(eq(getKey(expiredId)));
		verify(this.boundHashOperations).entries();
		verifyNoMoreInteractions(this.defaultSerializer);
		verifyNoMoreInteractions(this.publisher);
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	void flushModeOnSaveCreate() {
		this.redisRepository.createSession();

		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	void flushModeOnSaveSetAttribute() {
		RedisSession session = this.redisRepository.createSession();
		session.setAttribute("something", "here");

		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	void flushModeOnSaveRemoveAttribute() {
		RedisSession session = this.redisRepository.createSession();
		session.removeAttribute("remove");

		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	void flushModeOnSaveSetLastAccessedTime() {
		RedisSession session = this.redisRepository.createSession();
		session.setLastAccessedTime(Instant.ofEpochMilli(1L));

		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	void flushModeOnSaveSetMaxInactiveIntervalInSeconds() {
		RedisSession session = this.redisRepository.createSession();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));

		verifyNoMoreInteractions(this.boundHashOperations);
	}

	@Test
	void flushModeImmediateCreate() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.setFlushMode(FlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();

		Map<String, Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta.get(RedisSessionMapper.CREATION_TIME_KEY);
		assertThat(creationTime).isEqualTo(session.getCreationTime().toEpochMilli());
		assertThat(delta.get(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY))
				.isEqualTo((int) Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS).getSeconds());
		assertThat(delta.get(RedisSessionMapper.LAST_ACCESSED_TIME_KEY))
				.isEqualTo(session.getCreationTime().toEpochMilli());
	}

	@Test // gh-1409
	void flushModeImmediateCreateWithCustomMaxInactiveInterval() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);
		this.redisRepository.setDefaultMaxInactiveInterval(60);
		this.redisRepository.setFlushMode(FlushMode.IMMEDIATE);
		this.redisRepository.createSession();
		Map<String, Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		assertThat(delta.get(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY)).isEqualTo(60);
	}

	@Test
	void flushModeImmediateSetAttribute() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.setFlushMode(FlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();
		String attrName = "someAttribute";
		session.setAttribute(attrName, "someValue");

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(
				map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	void flushModeImmediateRemoveAttribute() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.setFlushMode(FlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();
		String attrName = "someAttribute";
		session.removeAttribute(attrName);

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(
				map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	@SuppressWarnings("unchecked")
	void flushModeSetMaxInactiveIntervalInSeconds() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.setFlushMode(FlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();

		reset(this.boundHashOperations);

		session.setMaxInactiveInterval(Duration.ofSeconds(1));

		verify(this.boundHashOperations).expire(anyLong(), any(TimeUnit.class));
	}

	@Test
	void flushModeSetLastAccessedTime() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);

		this.redisRepository.setFlushMode(FlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();

		session.setLastAccessedTime(Instant.now());

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(
				map(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, session.getLastAccessedTime().toEpochMilli()));
	}

	@Test
	void setFlushModeNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.redisRepository.setFlushMode(null))
				.withMessage("flushMode cannot be null");
	}

	@Test
	void changeRedisNamespace() {
		String namespace = "foo:bar";
		this.redisRepository.setRedisKeyNamespace(namespace);
		RedisSession session = this.redisRepository.new RedisSession(new MapSession(), false);
		session.setMaxInactiveInterval(Duration.ZERO);
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);

		this.redisRepository.save(session);

		String id = session.getId();
		verify(this.redisOperations, atLeastOnce()).delete(namespace + ":sessions:expires:" + id);
		verify(this.redisOperations, never()).boundValueOps(namespace + ":sessions:expires:" + id);
	}

	@Test
	void setRedisKeyNamespaceNullNamespace() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.redisRepository.setRedisKeyNamespace(null))
				.withMessage("namespace cannot be null or empty");
	}

	@Test
	void setRedisKeyNamespaceEmptyNamespace() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.redisRepository.setRedisKeyNamespace(" "))
				.withMessage("namespace cannot be null or empty");
	}

	@Test // gh-1120
	void getAttributeNamesAndRemove() {
		RedisSession session = this.redisRepository.new RedisSession(this.cached, false);
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	void onMessageCreatedInOtherDatabase() {
		JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.setDefaultSerializer(serializer);

		MapSession session = this.cached;
		String channel = "spring:session:event:created:1:" + session.getId();
		byte[] body = serializer.serialize(new HashMap());
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body);

		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		assertThat(this.event.getAllValues()).isEmpty();
		verifyNoMoreInteractions(this.publisher);
	}

	@Test
	void onMessageDeletedInOtherDatabase() {
		JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.setDefaultSerializer(serializer);

		MapSession session = this.cached;
		String channel = "__keyevent@1__:del";
		String body = "spring:session:sessions:expires:" + session.getId();
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));

		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		assertThat(this.event.getAllValues()).isEmpty();
		verifyNoMoreInteractions(this.publisher);
	}

	@Test
	void onMessageExpiredInOtherDatabase() {
		JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
		this.redisRepository.setApplicationEventPublisher(this.publisher);
		this.redisRepository.setDefaultSerializer(serializer);

		MapSession session = this.cached;
		String channel = "__keyevent@1__:expired";
		String body = "spring:session:sessions:expires:" + session.getId();
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));

		this.redisRepository.onMessage(message, "".getBytes(StandardCharsets.UTF_8));

		assertThat(this.event.getAllValues()).isEmpty();
		verifyNoMoreInteractions(this.publisher);
	}

	@Test
	void saveWithSaveModeOnSetAttribute() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);
		this.redisRepository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		RedisSession session = this.redisRepository.new RedisSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.redisRepository.save(session);
		assertThat(getDelta()).hasSize(1);
	}

	@Test
	void saveWithSaveModeOnGetAttribute() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);
		this.redisRepository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		RedisSession session = this.redisRepository.new RedisSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.redisRepository.save(session);
		assertThat(getDelta()).hasSize(2);
	}

	@Test
	void saveWithSaveModeAlways() {
		given(this.redisOperations.boundHashOps(anyString())).willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString())).willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString())).willReturn(this.boundValueOperations);
		this.redisRepository.setSaveMode(SaveMode.ALWAYS);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		RedisSession session = this.redisRepository.new RedisSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.redisRepository.save(session);
		assertThat(getDelta()).hasSize(3);
	}

	private String getKey(String id) {
		return "spring:session:sessions:" + id;
	}

	private Map map(Object... objects) {
		Map<String, Object> result = new HashMap<>();
		if (objects == null) {
			return result;
		}
		for (int i = 0; i < objects.length; i += 2) {
			result.put((String) objects[i], objects[i + 1]);
		}
		return result;
	}

	private Map<String, Object> getDelta() {
		return getDelta(1);
	}

	private Map<String, Object> getDelta(int times) {
		verify(this.boundHashOperations, times(times)).putAll(this.delta.capture());
		return this.delta.getAllValues().get(times - 1);
	}

}
