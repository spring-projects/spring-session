/*
 * Copyright 2014-2017 the original author or authors.
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.MapSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link ReactiveRedisOperationsSessionRepository}.
 *
 * @author Vedran Pavic
 */
public class ReactiveRedisOperationsSessionRepositoryTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

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

	@Before
	public void setUp() throws Exception {
		this.repository = new ReactiveRedisOperationsSessionRepository(
				this.redisOperations);
	}

	@Test
	public void constructorWithNullReactiveRedisOperations() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("sessionRedisOperations cannot be null");

		new ReactiveRedisOperationsSessionRepository(null);
	}

	@Test
	public void customRedisKeyNamespace() {
		this.repository.setRedisKeyNamespace("test");

		assertThat(ReflectionTestUtils.getField(this.repository, "keyPrefix")).isEqualTo("test:");
	}

	@Test
	public void nullRedisKeyNamespace() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("namespace cannot be null or empty");

		this.repository.setRedisKeyNamespace(null);
	}

	@Test
	public void emptyRedisKeyNamespace() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("namespace cannot be null or empty");

		this.repository.setRedisKeyNamespace("");
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
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("redisFlushMode cannot be null");

		this.repository.setRedisFlushMode(null);
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() {
		Mono<ReactiveRedisOperationsSessionRepository.RedisSession> session = this.repository
				.createSession();

		StepVerifier.create(session).expectNextMatches(predicate -> {
			assertThat(predicate.getMaxInactiveInterval()).isEqualTo(
					Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS));
			return true;
		});
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() {
		this.repository.setDefaultMaxInactiveInterval(600);
		Mono<ReactiveRedisOperationsSessionRepository.RedisSession> session = this.repository
				.createSession();

		StepVerifier.create(session).expectNextMatches(predicate -> {
			assertThat(predicate.getMaxInactiveInterval())
					.isEqualTo(Duration.ofSeconds(600));
			return true;
		});
	}

	@Test
	public void saveNewSession() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), this.delta.capture()))
				.willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession();
		Mono<Void> result = this.repository.save(session);

		StepVerifier.create(result).expectNextMatches(predicate -> {
			Map<String, Object> delta = this.delta.getAllValues().get(0);
			assertThat(delta.size()).isEqualTo(3);
			Object creationTime = delta
					.get(ReactiveRedisOperationsSessionRepository.CREATION_TIME_KEY);
			assertThat(creationTime).isEqualTo(session.getCreationTime().toEpochMilli());
			assertThat(delta.get(
					ReactiveRedisOperationsSessionRepository.MAX_INACTIVE_INTERVAL_KEY))
							.isEqualTo((int) Duration.ofSeconds(
									MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS)
									.getSeconds());
			assertThat(delta
					.get(ReactiveRedisOperationsSessionRepository.LAST_ACCESSED_TIME_KEY))
							.isEqualTo(session.getCreationTime().toEpochMilli());
			return true;
		});
	}

	@Test
	public void saveSessionNothingChanged() {
		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession(
				new MapSession());

		Mono<Void> result = this.repository.save(session);

		StepVerifier.create(result).expectNextMatches(predicate -> {
			verifyZeroInteractions(this.redisOperations);
			return true;
		});
	}

	@Test
	public void saveLastAccessChanged() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), this.delta.capture()))
				.willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession(
				new MapSession());
		session.setLastAccessedTime(Instant.ofEpochMilli(12345678L));
		Mono<Void> result = this.repository.save(session);

		StepVerifier.create(result).expectNextMatches(predicate -> {
			assertThat(this.delta.getAllValues().get(0))
					.isEqualTo(map(RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
							session.getLastAccessedTime().toEpochMilli()));
			return true;
		});
	}

	@Test
	public void saveSetAttribute() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), this.delta.capture()))
				.willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		String attrName = "attrName";
		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession(
				new MapSession());
		session.setAttribute(attrName, "attrValue");
		Mono<Void> result = this.repository.save(session);

		StepVerifier.create(result).expectNextMatches(predicate -> {
			assertThat(this.delta.getAllValues().get(0)).isEqualTo(
					map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
							session.getAttribute(attrName)));
			return true;
		});
	}

	@Test
	public void saveRemoveAttribute() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.putAll(anyString(), this.delta.capture()))
				.willReturn(Mono.just(true));
		given(this.redisOperations.expire(anyString(), any()))
				.willReturn(Mono.just(true));

		String attrName = "attrName";
		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession(
				new MapSession());
		session.removeAttribute(attrName);
		Mono<Void> result = this.repository.save(session);

		StepVerifier.create(result).expectNextMatches(predicate -> {
			assertThat(this.delta.getAllValues().get(0)).isEqualTo(
					map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
							null));
			return true;
		});
	}

	@Test
	public void redisSessionGetAttributes() {
		String attrName = "attrName";
		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession();
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute(attrName, "attrValue");
		assertThat(session.getAttributeNames()).containsOnly(attrName);

		session.removeAttribute(attrName);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void delete() {
		given(this.redisOperations.delete(anyString())).willReturn(Mono.just(1L));

		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository.new RedisSession(
				new MapSession());
		Mono<Void> result = this.repository.deleteById(session.getId());

		StepVerifier.create(result).expectNextMatches(predicate -> {
			assertThat(result).isEqualTo(1);
			return true;
		});
	}

	@Test
	public void getSessionNotFound() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		given(this.hashOperations.entries(anyString())).willReturn(Flux.empty());

		Mono<ReactiveRedisOperationsSessionRepository.RedisSession> session = this.repository
				.findById("test");

		StepVerifier.create(session).expectNextMatches(predicate -> {
			assertThat(predicate).isEqualTo(Mono.empty());
			return true;
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionFound() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(Instant.now().minusSeconds(60));
		expected.setAttribute(attrName, "attrValue");
		Map map = map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
				expected.getAttribute(attrName),
				RedisOperationsSessionRepository.CREATION_TIME_ATTR,
				expected.getCreationTime().toEpochMilli(),
				RedisOperationsSessionRepository.MAX_INACTIVE_ATTR,
				(int) expected.getMaxInactiveInterval().getSeconds(),
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
				expected.getLastAccessedTime().toEpochMilli());
		given(this.hashOperations.entries(anyString()))
				.willReturn(Flux.fromIterable(map.entrySet()));

		Mono<ReactiveRedisOperationsSessionRepository.RedisSession> session = this.repository
				.findById("test");

		StepVerifier.create(session).expectNextMatches(predicate -> {
			assertThat(predicate.getId()).isEqualTo(expected.getId());
			assertThat(predicate.getAttributeNames())
					.isEqualTo(expected.getAttributeNames());
			assertThat(predicate.<String>getAttribute(attrName))
					.isEqualTo(expected.getAttribute(attrName));
			assertThat(predicate.getCreationTime()).isEqualTo(expected.getCreationTime());
			assertThat(predicate.getMaxInactiveInterval())
					.isEqualTo(expected.getMaxInactiveInterval());
			assertThat(predicate.getLastAccessedTime())
					.isEqualTo(expected.getLastAccessedTime());
			return true;
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionExpired() {
		given(this.redisOperations.opsForHash()).willReturn(this.hashOperations);
		Map map = map(RedisOperationsSessionRepository.MAX_INACTIVE_ATTR, 1,
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
				Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());
		given(this.hashOperations.entries(anyString()))
				.willReturn(Flux.fromIterable(map.entrySet()));

		Mono<ReactiveRedisOperationsSessionRepository.RedisSession> session = this.repository
				.findById("test");

		StepVerifier.create(session).expectNextMatches(predicate -> {
			assertThat(predicate).isNull();
			return true;
		});
	}

	// TODO

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
