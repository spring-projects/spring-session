/*
 * Copyright 2014-present the original author or authors.
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

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository.RedisSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ReactiveRedisIndexedSessionRepository}.
 *
 * @author Burak Kalaycı
 */
@SuppressWarnings("unchecked")
class ReactiveRedisIndexedSessionRepositoryTests {

	private final ReactiveRedisOperations<String, Object> sessionRedisOperations = mock(Answers.RETURNS_DEEP_STUBS);

	private final ReactiveRedisTemplate<String, String> keyEventsOperations = mock(Answers.RETURNS_DEEP_STUBS);

	private final SortedSetReactiveRedisSessionExpirationStore expirationStore = mock(
			SortedSetReactiveRedisSessionExpirationStore.class);

	private ReactiveRedisIndexedSessionRepository repository;

	@BeforeEach
	void setUp() {
		given(this.sessionRedisOperations.rename(anyString(), anyString())).willReturn(Mono.empty());
		given(this.sessionRedisOperations.delete(anyString())).willReturn(Mono.just(1L));
		given(this.sessionRedisOperations.opsForHash().putAll(anyString(), any(Map.class))).willReturn(Mono.just(true));
		given(this.sessionRedisOperations.opsForValue().append(anyString(), anyString())).willReturn(Mono.just(1L));
		given(this.sessionRedisOperations.expire(anyString(), any(Duration.class))).willReturn(Mono.just(true));
		given(this.sessionRedisOperations.opsForSet().add(anyString(), any())).willReturn(Mono.just(1L));
		given(this.sessionRedisOperations.opsForSet().remove(anyString(), any())).willReturn(Mono.just(1L));
		given(this.sessionRedisOperations.opsForSet().members(anyString())).willReturn(Flux.empty());
		given(this.expirationStore.add(anyString(), any())).willReturn(Mono.empty());
		given(this.expirationStore.remove(anyString())).willReturn(Mono.empty());

		this.repository = new ReactiveRedisIndexedSessionRepository(this.sessionRedisOperations,
				this.keyEventsOperations);
		ReflectionTestUtils.setField(this.repository, "expirationStore", this.expirationStore);
		this.repository.setSessionIdGenerator(() -> "new-session-id");
	}

	@Test
	void saveWhenSessionIdChangedThenRemovesOriginalIdFromExpirationStore() throws Exception {
		MapSession cached = new MapSession("original-session-id");
		cached.setCreationTime(Instant.ofEpochMilli(1_700_000_000_000L));
		cached.setLastAccessedTime(Instant.ofEpochMilli(1_700_000_000_000L));
		cached.setMaxInactiveInterval(Duration.ofMinutes(30));

		RedisSession session = newRedisSession(cached, false);
		String originalId = session.getId();
		String changedId = session.changeSessionId();

		assertThat(originalId).isEqualTo("original-session-id");
		assertThat(changedId).isEqualTo("new-session-id");

		StepVerifier.create(this.repository.save(session)).verifyComplete();

		verify(this.expirationStore).remove(eq(originalId));
		verify(this.expirationStore).add(eq(changedId), any());
		verify(this.expirationStore, never()).remove(eq(changedId));
	}

	private RedisSession newRedisSession(MapSession cached, boolean isNew) throws Exception {
		Class<?> redisSessionClass = null;
		for (Class<?> nested : ReactiveRedisIndexedSessionRepository.class.getDeclaredClasses()) {
			if (nested.getSimpleName().equals("RedisSession")) {
				redisSessionClass = nested;
				break;
			}
		}
		assertThat(redisSessionClass).isNotNull();
		Constructor<?> constructor = redisSessionClass
			.getDeclaredConstructor(ReactiveRedisIndexedSessionRepository.class, MapSession.class, boolean.class);
		constructor.setAccessible(true);
		return (RedisSession) constructor.newInstance(this.repository, cached, isNew);
	}

}
