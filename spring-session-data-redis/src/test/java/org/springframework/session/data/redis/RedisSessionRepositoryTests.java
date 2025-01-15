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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.data.redis.RedisSessionRepository.RedisSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link RedisSessionRepository}.
 *
 * @author Vedran Pavic
 */
@ExtendWith(MockitoExtension.class)
class RedisSessionRepositoryTests {

	private static final String TEST_SESSION_ID = "session-id";

	private static final String TEST_SESSION_KEY = getSessionKey(TEST_SESSION_ID);

	@Mock(strictness = Mock.Strictness.LENIENT)
	private RedisOperations<String, Object> sessionRedisOperations;

	@Mock
	private HashOperations<String, String, Object> sessionHashOperations;

	@Captor
	private ArgumentCaptor<Map<String, Object>> delta;

	private RedisSessionRepository sessionRepository;

	@BeforeEach
	void setUp() {
		given(this.sessionRedisOperations.<String, Object>opsForHash()).willReturn(this.sessionHashOperations);
		this.sessionRepository = new RedisSessionRepository(this.sessionRedisOperations);
	}

	@Test
	void constructor_NullRedisOperations_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReactiveRedisSessionRepository(null))
			.withMessageContaining("sessionRedisOperations cannot be null");
	}

	@Test
	void setDefaultMaxInactiveInterval_ValidInterval_ShouldSetInterval() {
		this.sessionRepository.setDefaultMaxInactiveInterval(Duration.ofMinutes(10));
		assertThat(ReflectionTestUtils.getField(this.sessionRepository, "defaultMaxInactiveInterval"))
			.isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void setDefaultMaxInactiveInterval_NullInterval_ShouldThrowException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.sessionRepository.setDefaultMaxInactiveInterval(null))
			.withMessage("defaultMaxInactiveInterval must not be null");
	}

	@Test
	void setRedisKeyNamespace_ValidNamespace_ShouldSetNamespace() {
		this.sessionRepository.setRedisKeyNamespace("test");
		assertThat(ReflectionTestUtils.getField(this.sessionRepository, "keyNamespace")).isEqualTo("test:");
	}

	@Test
	void setRedisKeyNamespace_NullNamespace_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.sessionRepository.setRedisKeyNamespace(null))
			.withMessage("namespace must not be empty");
	}

	@Test
	void setRedisKeyNamespace_EmptyNamespace_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.sessionRepository.setRedisKeyNamespace(" "))
			.withMessage("namespace must not be empty");
	}

	@Test
	void setFlushMode_ValidFlushMode_ShouldSetFlushMode() {
		this.sessionRepository.setFlushMode(FlushMode.IMMEDIATE);
		assertThat(ReflectionTestUtils.getField(this.sessionRepository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void setFlushMode_NullFlushMode_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.sessionRepository.setFlushMode(null))
			.withMessage("flushMode must not be null");
	}

	@Test
	void setSaveMode_ValidSaveMode_ShouldSetSaveMode() {
		this.sessionRepository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
		assertThat(ReflectionTestUtils.getField(this.sessionRepository, "saveMode"))
			.isEqualTo(SaveMode.ON_GET_ATTRIBUTE);
	}

	@Test
	void setSaveMode_NullSaveMode_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.sessionRepository.setSaveMode(null))
			.withMessage("saveMode must not be null");
	}

	@Test
	void createSession_DefaultMaxInactiveInterval_ShouldCreateSession() {
		RedisSession redisSession = this.sessionRepository.createSession();
		assertThat(redisSession.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void createSession_CustomMaxInactiveInterval_ShouldCreateSession() {
		this.sessionRepository.setDefaultMaxInactiveInterval(Duration.ofMinutes(10));
		RedisSession redisSession = this.sessionRepository.createSession();
		assertThat(redisSession.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(10));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void createSession_ImmediateFlushMode_ShouldCreateSession() {
		this.sessionRepository.setFlushMode(FlushMode.IMMEDIATE);
		RedisSession session = this.sessionRepository.createSession();
		String key = getSessionKey(session.getId());
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(key), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(key), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(3);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_NewSession_ShouldSaveSession() {
		RedisSession session = this.sessionRepository.createSession();
		this.sessionRepository.save(session);
		String key = getSessionKey(session.getId());
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(key), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(key), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(3);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_NewSessionAndCustomKeyNamespace_ShouldSaveSession() {
		this.sessionRepository.setRedisKeyNamespace("custom");
		RedisSession session = this.sessionRepository.createSession();
		this.sessionRepository.save(session);
		String key = "custom:sessions:" + session.getId();
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(key), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(key), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(3);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_NewSessionAndChangedSessionId_ShouldSaveSession() {
		RedisSession session = this.sessionRepository.createSession();
		session.changeSessionId();
		this.sessionRepository.save(session);
		String key = getSessionKey(session.getId());
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(key), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(key), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(3);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_SessionExistsAndHasChanges_ShouldSaveSession() {
		given(this.sessionRedisOperations.hasKey(eq(TEST_SESSION_KEY))).willReturn(true);
		RedisSession session = createTestSession();
		session.setLastAccessedTime(Instant.now());
		session.setAttribute("attribute2", "value2");
		this.sessionRepository.save(session);
		verify(this.sessionRedisOperations).hasKey(eq(TEST_SESSION_KEY));
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(TEST_SESSION_KEY), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(TEST_SESSION_KEY), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(2);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_SessionExistsAndNoChanges_ShouldSaveSession() {
		given(this.sessionRedisOperations.hasKey(eq(TEST_SESSION_KEY))).willReturn(true);
		RedisSession session = createTestSession();
		this.sessionRepository.save(session);
		verify(this.sessionRedisOperations).hasKey(eq(TEST_SESSION_KEY));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_WithSaveModeOnSetAttribute_ShouldSaveSession() {
		given(this.sessionRedisOperations.hasKey(eq(TEST_SESSION_KEY))).willReturn(true);
		this.sessionRepository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("attribute1", "value1");
		attributes.put("attribute2", "value2");
		attributes.put("attribute3", "value3");
		RedisSession session = createTestSession(attributes);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.sessionRepository.save(session);
		verify(this.sessionRedisOperations).hasKey(eq(TEST_SESSION_KEY));
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(TEST_SESSION_KEY), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(TEST_SESSION_KEY), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(1);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void saveWithSaveModeOnGetAttribute() {
		given(this.sessionRedisOperations.hasKey(eq(TEST_SESSION_KEY))).willReturn(true);
		this.sessionRepository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("attribute1", "value1");
		attributes.put("attribute2", "value2");
		attributes.put("attribute3", "value3");
		RedisSession session = createTestSession(attributes);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.sessionRepository.save(session);
		verify(this.sessionRedisOperations).hasKey(eq(TEST_SESSION_KEY));
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(TEST_SESSION_KEY), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(TEST_SESSION_KEY), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(2);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void saveWithSaveModeAlways() {
		given(this.sessionRedisOperations.hasKey(eq(TEST_SESSION_KEY))).willReturn(true);
		this.sessionRepository.setSaveMode(SaveMode.ALWAYS);
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("attribute1", "value1");
		attributes.put("attribute2", "value2");
		attributes.put("attribute3", "value3");
		RedisSession session = createTestSession(attributes);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.sessionRepository.save(session);
		verify(this.sessionRedisOperations).hasKey(eq(TEST_SESSION_KEY));
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionRedisOperations).expireAt(eq(TEST_SESSION_KEY), eq(getExpiry(session)));
		verify(this.sessionHashOperations).putAll(eq(TEST_SESSION_KEY), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(3);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void save_SessionNotExists_ShouldThrowException() {
		RedisSession session = createTestSession();
		assertThatIllegalStateException().isThrownBy(() -> this.sessionRepository.save(session))
			.withMessage("Session was invalidated");
		verify(this.sessionRedisOperations).hasKey(eq(TEST_SESSION_KEY));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void findById_SessionExists_ShouldReturnSession() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		given(this.sessionHashOperations.entries(eq(TEST_SESSION_KEY)))
			.willReturn(mapOf(RedisSessionMapper.CREATION_TIME_KEY, Instant.EPOCH.toEpochMilli(),
					RedisSessionMapper.LAST_ACCESSED_TIME_KEY, now.toEpochMilli(),
					RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS,
					RedisSessionMapper.ATTRIBUTE_PREFIX + "attribute1", "value1"));
		RedisSession session = this.sessionRepository.findById(TEST_SESSION_ID);
		assertThat(session.getId()).isEqualTo(TEST_SESSION_ID);
		assertThat(session.getCreationTime()).isEqualTo(Instant.EPOCH);
		assertThat(session.getLastAccessedTime()).isEqualTo(now);
		assertThat(session.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS));
		assertThat(session.getAttributeNames()).isEqualTo(Collections.singleton("attribute1"));
		assertThat(session.<String>getAttribute("attribute1")).isEqualTo("value1");
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionHashOperations).entries(eq(TEST_SESSION_KEY));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void findById_SessionExistsAndIsExpired_ShouldReturnNull() {
		given(this.sessionHashOperations.entries(eq(TEST_SESSION_KEY)))
			.willReturn(mapOf(RedisSessionMapper.CREATION_TIME_KEY, Instant.EPOCH.toEpochMilli(),
					RedisSessionMapper.LAST_ACCESSED_TIME_KEY, Instant.EPOCH.toEpochMilli(),
					RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS,
					RedisSessionMapper.ATTRIBUTE_PREFIX + "attribute1", "value1"));
		assertThat(this.sessionRepository.findById(TEST_SESSION_ID)).isNull();
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionHashOperations).entries(eq(TEST_SESSION_KEY));
		verify(this.sessionRedisOperations).delete(eq(TEST_SESSION_KEY));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void findById_SessionNotExists_ShouldReturnNull() {
		assertThat(this.sessionRepository.findById(TEST_SESSION_ID)).isNull();
		verify(this.sessionRedisOperations).opsForHash();
		verify(this.sessionHashOperations).entries(eq(TEST_SESSION_KEY));
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void deleteById__ShouldDeleteSession() {
		this.sessionRepository.deleteById(TEST_SESSION_ID);
		verify(this.sessionRedisOperations).delete(TEST_SESSION_KEY);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void getSessionRedisOperations__ShouldReturnRedisOperations() {
		assertThat(this.sessionRepository.getSessionRedisOperations()).isEqualTo(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionRedisOperations);
		verifyNoMoreInteractions(this.sessionHashOperations);
	}

	@Test
	void createSessionWhenSessionIdGeneratorThenUses() {
		this.sessionRepository.setSessionIdGenerator(() -> "test");
		RedisSessionRepository.RedisSession session = this.sessionRepository.createSession();
		assertThat(session.getId()).isEqualTo("test");
		assertThat(session.changeSessionId()).isEqualTo("test");
	}

	@Test
	void setSessionIdGeneratorWhenNullThenThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.sessionRepository.setSessionIdGenerator(null))
			.withMessage("sessionIdGenerator cannot be null");
	}

	@Test
	void findByIdWhenChangeSessionIdThenUsesSessionIdGenerator() {
		this.sessionRepository.setSessionIdGenerator(() -> "test");
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		given(this.sessionHashOperations.entries(eq(TEST_SESSION_KEY)))
			.willReturn(mapOf(RedisSessionMapper.CREATION_TIME_KEY, Instant.EPOCH.toEpochMilli(),
					RedisSessionMapper.LAST_ACCESSED_TIME_KEY, now.toEpochMilli(),
					RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS,
					RedisSessionMapper.ATTRIBUTE_PREFIX + "attribute1", "value1"));
		RedisSession session = this.sessionRepository.findById(TEST_SESSION_ID);
		assertThat(session.getId()).isEqualTo(TEST_SESSION_ID);
		assertThat(session.changeSessionId()).isEqualTo("test");
	}

	private static String getSessionKey(String sessionId) {
		return "spring:session:sessions:" + sessionId;
	}

	private static Instant getExpiry(RedisSession session) {
		return Instant.ofEpochMilli(session.getLastAccessedTime().toEpochMilli())
			.plusSeconds(session.getMaxInactiveInterval().getSeconds());
	}

	private static Map<String, Object> mapOf(Object... objects) {
		Map<String, Object> result = new HashMap<>();
		if (objects != null) {
			for (int i = 0; i < objects.length; i += 2) {
				result.put((String) objects[i], objects[i + 1]);
			}
		}
		return result;
	}

	private RedisSession createTestSession(Map<String, Object> attributes) {
		MapSession cached = new MapSession(TEST_SESSION_ID);
		cached.setCreationTime(Instant.EPOCH);
		cached.setLastAccessedTime(Instant.EPOCH);
		attributes.forEach(cached::setAttribute);
		return this.sessionRepository.new RedisSession(cached, false);
	}

	private RedisSession createTestSession() {
		return createTestSession(Collections.singletonMap("attribute1", "value1"));
	}

}
