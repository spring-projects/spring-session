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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository.RedisSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link ReactiveRedisSessionRepository}.
 *
 * @author Vedran Pavic
 */
class ReactiveRedisSessionRepositoryTests {

	@SuppressWarnings("unchecked")
	private ReactiveRedisOperations<String, Object> redisOperations = mock(ReactiveRedisOperations.class);

	@SuppressWarnings("unchecked")
	private ReactiveHashOperations<String, Object, Object> hashOperations = mock(ReactiveHashOperations.class);

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Map<String, Object>> delta = ArgumentCaptor.forClass(Map.class);

	private ReactiveRedisSessionRepository repository;

	private MapSession cached;

	@BeforeEach
	void setUp() {
		this.repository = new ReactiveRedisSessionRepository(this.redisOperations);

		this.cached = new MapSession();
		this.cached.setId("session-id");
		this.cached.setCreationTime(Instant.ofEpochMilli(1404360000000L));
		this.cached.setLastAccessedTime(Instant.ofEpochMilli(1404360000000L));
	}

	@Test
	void constructorWithNullReactiveRedisOperations() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReactiveRedisSessionRepository(null))
				.withMessageContaining("sessionRedisOperations cannot be null");
	}

	@Test
	void customRedisKeyNamespace() {
		this.repository.setRedisKeyNamespace("test");

		assertThat(ReflectionTestUtils.getField(this.repository, "namespace")).isEqualTo("test:");
	}

	@Test
	void nullRedisKeyNamespace() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setRedisKeyNamespace(null))
				.withMessage("namespace cannot be null or empty");
	}

	@Test
	void emptyRedisKeyNamespace() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setRedisKeyNamespace(""))
				.withMessage("namespace cannot be null or empty");
	}

	@Test
	void customMaxInactiveInterval() {
		this.repository.setDefaultMaxInactiveInterval(600);

		assertThat(ReflectionTestUtils.getField(this.repository, "defaultMaxInactiveInterval")).isEqualTo(600);
	}

	@Test
	void createSessionDefaultMaxInactiveInterval() {
		StepVerifier.create(this.repository.createSession())
				.consumeNextWith((session) -> assertThat(session.getMaxInactiveInterval())
						.isEqualTo(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS)))
				.verifyComplete();
	}

	@Test
	void createSessionCustomMaxInactiveInterval() {
		this.repository.setDefaultMaxInactiveInterval(600);

		StepVerifier.create(this.repository.createSession())
				.consumeNextWith(
						(session) -> assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(600)))
				.verifyComplete();
	}

	@Test
	void saveNewSession() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));

		RedisSession newSession = this.repository.new RedisSession(new MapSession(), true);
		StepVerifier.create(this.repository.save(newSession)).verifyComplete();

		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);

		Map<String, Object> delta = this.delta.getAllValues().get(0);
		assertThat(delta.size()).isEqualTo(3);
		assertThat(delta.get(RedisSessionMapper.CREATION_TIME_KEY))
				.isEqualTo(newSession.getCreationTime().toEpochMilli());
		assertThat(delta.get(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY))
				.isEqualTo((int) newSession.getMaxInactiveInterval().getSeconds());
		assertThat(delta.get(RedisSessionMapper.LAST_ACCESSED_TIME_KEY))
				.isEqualTo(newSession.getLastAccessedTime().toEpochMilli());
	}

	@Test
	void saveSessionNothingChanged() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));

		RedisSession session = this.repository.new RedisSession(this.cached, false);

		StepVerifier.create(this.repository.save(session)).verifyComplete();

		verify(this.redisOperations).hasKey(anyString());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	@Test
	void saveLastAccessChanged() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));

		RedisSession session = this.repository.new RedisSession(this.cached, false);
		session.setLastAccessedTime(Instant.ofEpochMilli(12345678L));
		StepVerifier.create(this.repository.save(session)).verifyComplete();

		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);

		assertThat(this.delta.getAllValues().get(0)).isEqualTo(
				map(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, session.getLastAccessedTime().toEpochMilli()));
	}

	@Test
	void saveSetAttribute() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));

		String attrName = "attrName";
		RedisSession session = this.repository.new RedisSession(this.cached, false);
		session.setAttribute(attrName, "attrValue");
		StepVerifier.create(this.repository.save(session)).verifyComplete();

		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);

		assertThat(this.delta.getAllValues().get(0)).isEqualTo(
				map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	void saveRemoveAttribute() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));

		String attrName = "attrName";
		RedisSession session = this.repository.new RedisSession(new MapSession(), false);
		session.removeAttribute(attrName);
		StepVerifier.create(this.repository.save(session)).verifyComplete();

		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);

		assertThat(this.delta.getAllValues().get(0))
				.isEqualTo(map(RedisIndexedSessionRepository.getSessionAttrNameKey(attrName), null));
	}

	@Test
	void redisSessionGetAttributes() {
		String attrName = "attrName";
		RedisSession session = this.repository.new RedisSession(this.cached, false);
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute(attrName, "attrValue");
		assertThat(session.getAttributeNames()).containsOnly(attrName);

		session.removeAttribute(attrName);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	void delete() {
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(1L));

		StepVerifier.create(this.repository.deleteById("test")).verifyComplete();

		verify(this.redisOperations).delete(anyString());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	@Test
	void getSessionNotFound() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.entries(anyString())).willReturn(Flux.empty());
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(0L));

		StepVerifier.create(this.repository.findById("test")).verifyComplete();

		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).entries(anyString());
		verify(this.redisOperations).delete(anyString());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	void getSessionFound() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		String attribute1 = "attribute1";
		String attribute2 = "attribute2";
		MapSession expected = new MapSession("test");
		expected.setLastAccessedTime(Instant.now().minusSeconds(60));
		expected.setAttribute(attribute1, "test");
		expected.setAttribute(attribute2, null);
		Map map = map(RedisSessionMapper.ATTRIBUTE_PREFIX + attribute1, expected.getAttribute(attribute1),
				RedisSessionMapper.ATTRIBUTE_PREFIX + attribute2, expected.getAttribute(attribute2),
				RedisSessionMapper.CREATION_TIME_KEY, expected.getCreationTime().toEpochMilli(),
				RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) expected.getMaxInactiveInterval().getSeconds(),
				RedisSessionMapper.LAST_ACCESSED_TIME_KEY, expected.getLastAccessedTime().toEpochMilli());
		given(this.hashOperations.entries(anyString())).willReturn(Flux.fromIterable(map.entrySet()));

		StepVerifier.create(this.repository.findById("test")).consumeNextWith((session) -> {
			verify(this.redisOperations).opsForHash();
			verify(this.hashOperations).entries(anyString());
			verifyNoMoreInteractions(this.redisOperations);
			verifyNoMoreInteractions(this.hashOperations);

			assertThat(session.getId()).isEqualTo(expected.getId());
			assertThat(session.getAttributeNames()).isEqualTo(expected.getAttributeNames());
			assertThat(session.<String>getAttribute(attribute1)).isEqualTo(expected.getAttribute(attribute1));
			assertThat(session.<String>getAttribute(attribute2)).isEqualTo(expected.getAttribute(attribute2));
			assertThat(session.getCreationTime().truncatedTo(ChronoUnit.MILLIS))
					.isEqualTo(expected.getCreationTime().truncatedTo(ChronoUnit.MILLIS));
			assertThat(session.getMaxInactiveInterval()).isEqualTo(expected.getMaxInactiveInterval());
			assertThat(session.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS))
					.isEqualTo(expected.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS));
		}).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void getSessionExpired() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		Map map = map(RedisSessionMapper.CREATION_TIME_KEY, 0L, RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1,
				RedisSessionMapper.LAST_ACCESSED_TIME_KEY, Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());
		given(this.hashOperations.entries(anyString())).willReturn(Flux.fromIterable(map.entrySet()));
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(0L));

		StepVerifier.create(this.repository.findById("test")).verifyComplete();

		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).entries(anyString());
		verify(this.redisOperations).delete(anyString());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	@Test // gh-1120
	void getAttributeNamesAndRemove() {
		RedisSession session = this.repository.new RedisSession(this.cached, false);
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	void saveWithSaveModeOnSetAttribute() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));
		this.repository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		RedisSession session = this.repository.new RedisSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		StepVerifier.create(this.repository.save(session)).verifyComplete();
		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(1);
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	@Test
	void saveWithSaveModeOnGetAttribute() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));
		this.repository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		RedisSession session = this.repository.new RedisSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		StepVerifier.create(this.repository.save(session)).verifyComplete();
		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(2);
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	@Test
	void saveWithSaveModeAlways() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any())).willReturn(Mono.just(true));
		this.repository.setSaveMode(SaveMode.ALWAYS);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		RedisSession session = this.repository.new RedisSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		StepVerifier.create(this.repository.save(session)).verifyComplete();
		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		assertThat(this.delta.getValue()).hasSize(3);
		verify(this.redisOperations).expire(anyString(), any());
		verifyNoMoreInteractions(this.redisOperations);
		verifyNoMoreInteractions(this.hashOperations);
	}

	private Map<String, Object> map(Object... objects) {
		Map<String, Object> result = new HashMap<>();
		if (objects == null) {
			return result;
		}
		for (int i = 0; i < objects.length; i += 2) {
			result.put((String) objects[i], objects[i + 1]);
		}
		return result;
	}

}
