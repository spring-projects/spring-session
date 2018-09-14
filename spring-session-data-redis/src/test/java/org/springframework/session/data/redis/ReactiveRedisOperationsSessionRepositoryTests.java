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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.ReactiveRedisOperationsSessionRepository.RedisSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link ReactiveRedisOperationsSessionRepository}.
 *
 * @author Vedran Pavic
 */
public class ReactiveRedisOperationsSessionRepositoryTests {

	@SuppressWarnings("unchecked")
	private ReactiveRedisOperations<String, Object> redisOperations = mock(
			ReactiveRedisOperations.class);

	@SuppressWarnings("unchecked")
	private ReactiveHashOperations<String, Object, Object> hashOperations = mock(
			ReactiveHashOperations.class);

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Map<String, Object>> delta = ArgumentCaptor
			.forClass(Map.class);

	private ReactiveRedisOperationsSessionRepository repository;

	private MapSession cached;

	@Before
	public void setUp() {
		this.repository = new ReactiveRedisOperationsSessionRepository(
				this.redisOperations);

		this.cached = new MapSession();
		this.cached.setId("session-id");
		this.cached.setCreationTime(Instant.ofEpochMilli(1404360000000L));
		this.cached.setLastAccessedTime(Instant.ofEpochMilli(1404360000000L));
	}

	@Test
	public void constructorWithNullReactiveRedisOperations() {
		assertThatThrownBy(() -> new ReactiveRedisOperationsSessionRepository(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("sessionRedisOperations cannot be null");
	}

	@Test
	public void customRedisKeyNamespace() {
		this.repository.setRedisKeyNamespace("test");

		assertThat(ReflectionTestUtils.getField(this.repository, "namespace"))
				.isEqualTo("test:");
	}

	@Test
	public void nullRedisKeyNamespace() {
		assertThatThrownBy(() -> this.repository.setRedisKeyNamespace(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("namespace cannot be null or empty");
	}

	@Test
	public void emptyRedisKeyNamespace() {
		assertThatThrownBy(() -> this.repository.setRedisKeyNamespace(""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("namespace cannot be null or empty");
	}

	@Test
	public void customMaxInactiveInterval() {
		this.repository.setDefaultMaxInactiveInterval(600);

		assertThat(ReflectionTestUtils.getField(this.repository,
				"defaultMaxInactiveInterval")).isEqualTo(600);
	}

	@Test
	public void customRedisFlushMode() {
		this.repository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);

		assertThat(ReflectionTestUtils.getField(this.repository, "redisFlushMode"))
				.isEqualTo(RedisFlushMode.IMMEDIATE);
	}

	@Test
	public void nullRedisFlushMode() {
		assertThatThrownBy(() -> this.repository.setRedisFlushMode(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("redisFlushMode cannot be null");
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() {
		StepVerifier.create(this.repository.createSession()).consumeNextWith(
				(session) -> assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration
						.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS)))
				.verifyComplete();
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() {
		this.repository.setDefaultMaxInactiveInterval(600);

		StepVerifier.create(this.repository.createSession())
				.consumeNextWith((session) -> assertThat(session.getMaxInactiveInterval())
						.isEqualTo(Duration.ofSeconds(600)))
				.verifyComplete();
	}

	@Test
	public void saveNewSession() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		StepVerifier
				.create(this.repository.createSession().doOnNext(this.repository::save))
				.consumeNextWith((session) -> {
					verify(this.redisOperations).opsForHash();
					verify(this.hashOperations).putAll(anyString(), this.delta.capture());
					verify(this.redisOperations).expire(anyString(), any());
					verifyZeroInteractions(this.redisOperations);
					verifyZeroInteractions(this.hashOperations);

					Map<String, Object> delta = this.delta.getAllValues().get(0);
					assertThat(delta.size()).isEqualTo(3);
					assertThat(delta.get(
							ReactiveRedisOperationsSessionRepository.CREATION_TIME_KEY))
									.isEqualTo(session.getCreationTime().toEpochMilli());
					assertThat(delta.get(
							ReactiveRedisOperationsSessionRepository.MAX_INACTIVE_INTERVAL_KEY))
									.isEqualTo((int) Duration.ofSeconds(
											MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS)
											.getSeconds());
					assertThat(delta.get(
							ReactiveRedisOperationsSessionRepository.LAST_ACCESSED_TIME_KEY))
									.isEqualTo(
											session.getLastAccessedTime().toEpochMilli());
				}).verifyComplete();
	}

	@Test
	public void saveSessionNothingChanged() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		RedisSession session = this.repository.new RedisSession(
				new MapSession(this.cached));

		StepVerifier.create(this.repository.save(session)).verifyComplete();

		verify(this.redisOperations).hasKey(anyString());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);
	}

	@Test
	public void saveLastAccessChanged() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		RedisSession session = this.repository.new RedisSession(this.cached);
		session.setLastAccessedTime(Instant.ofEpochMilli(12345678L));
		Mono.just(session).subscribe(this.repository::save);

		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);

		assertThat(this.delta.getAllValues().get(0))
				.isEqualTo(map(RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
						session.getLastAccessedTime().toEpochMilli()));
	}

	@Test
	public void saveSetAttribute() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		String attrName = "attrName";
		RedisSession session = this.repository.new RedisSession(this.cached);
		session.setAttribute(attrName, "attrValue");
		Mono.just(session).subscribe(this.repository::save);

		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);

		assertThat(this.delta.getAllValues().get(0)).isEqualTo(
				map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
						session.getAttribute(attrName)));
	}

	@Test
	public void saveRemoveAttribute() {
		given(this.redisOperations.hasKey(anyString())).willReturn(Mono.just(true));
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), any())).willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		String attrName = "attrName";
		RedisSession session = this.repository.new RedisSession(new MapSession());
		session.removeAttribute(attrName);
		Mono.just(session).subscribe(this.repository::save);

		verify(this.redisOperations).hasKey(anyString());
		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).putAll(anyString(), this.delta.capture());
		verify(this.redisOperations).expire(anyString(), any());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);

		assertThat(this.delta.getAllValues().get(0)).isEqualTo(map(
				RedisOperationsSessionRepository.getSessionAttrNameKey(attrName), null));
	}

	@Test
	public void redisSessionGetAttributes() {
		String attrName = "attrName";
		RedisSession session = this.repository.new RedisSession(this.cached);
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute(attrName, "attrValue");
		assertThat(session.getAttributeNames()).containsOnly(attrName);

		session.removeAttribute(attrName);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void delete() {
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(1L));

		StepVerifier.create(this.repository.deleteById("test")).verifyComplete();

		verify(this.redisOperations).delete(anyString());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);
	}

	@Test
	public void getSessionNotFound() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.entries(anyString())).willReturn(Flux.empty());
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(0L));

		StepVerifier.create(this.repository.findById("test")).verifyComplete();

		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).entries(anyString());
		verify(this.redisOperations).delete(anyString());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionFound() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		String attribute1 = "attribute1";
		String attribute2 = "attribute2";
		MapSession expected = new MapSession("test");
		expected.setLastAccessedTime(Instant.now().minusSeconds(60));
		expected.setAttribute(attribute1, "test");
		expected.setAttribute(attribute2, null);
		Map map = map(
				ReactiveRedisOperationsSessionRepository.ATTRIBUTE_PREFIX + attribute1,
				expected.getAttribute(attribute1),
				ReactiveRedisOperationsSessionRepository.ATTRIBUTE_PREFIX + attribute2,
				expected.getAttribute(attribute2),
				ReactiveRedisOperationsSessionRepository.CREATION_TIME_KEY,
				expected.getCreationTime().toEpochMilli(),
				ReactiveRedisOperationsSessionRepository.MAX_INACTIVE_INTERVAL_KEY,
				(int) expected.getMaxInactiveInterval().getSeconds(),
				ReactiveRedisOperationsSessionRepository.LAST_ACCESSED_TIME_KEY,
				expected.getLastAccessedTime().toEpochMilli());
		given(this.hashOperations.entries(anyString()))
				.willReturn(Flux.fromIterable(map.entrySet()));

		StepVerifier.create(this.repository.findById("test")).consumeNextWith((session) -> {
			verify(this.redisOperations).opsForHash();
			verify(this.hashOperations).entries(anyString());
			verifyZeroInteractions(this.redisOperations);
			verifyZeroInteractions(this.hashOperations);

			assertThat(session.getId()).isEqualTo(expected.getId());
			assertThat(session.getAttributeNames())
					.isEqualTo(expected.getAttributeNames());
			assertThat(session.<String>getAttribute(attribute1))
					.isEqualTo(expected.getAttribute(attribute1));
			assertThat(session.<String>getAttribute(attribute2))
					.isEqualTo(expected.getAttribute(attribute2));
					assertThat(session.getCreationTime().truncatedTo(ChronoUnit.MILLIS))
							.isEqualTo(expected.getCreationTime()
									.truncatedTo(ChronoUnit.MILLIS));
					assertThat(session.getMaxInactiveInterval())
					.isEqualTo(expected.getMaxInactiveInterval());
					assertThat(
							session.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS))
									.isEqualTo(expected.getLastAccessedTime()
											.truncatedTo(ChronoUnit.MILLIS));
				}).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionExpired() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		Map map = map(ReactiveRedisOperationsSessionRepository.CREATION_TIME_KEY, 0L,
				ReactiveRedisOperationsSessionRepository.MAX_INACTIVE_INTERVAL_KEY, 1,
				ReactiveRedisOperationsSessionRepository.LAST_ACCESSED_TIME_KEY,
				Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());
		given(this.hashOperations.entries(anyString()))
				.willReturn(Flux.fromIterable(map.entrySet()));
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(0L));

		StepVerifier.create(this.repository.findById("test")).verifyComplete();

		verify(this.redisOperations).opsForHash();
		verify(this.hashOperations).entries(anyString());
		verify(this.redisOperations).delete(anyString());
		verifyZeroInteractions(this.redisOperations);
		verifyZeroInteractions(this.hashOperations);
	}

	@Test // gh-1120
	public void getAttributeNamesAndRemove() {
		RedisSession session = this.repository.new RedisSession(this.cached);
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
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
