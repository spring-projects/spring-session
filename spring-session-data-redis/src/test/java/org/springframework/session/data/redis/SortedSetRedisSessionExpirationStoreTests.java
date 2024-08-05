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

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SortedSetRedisSessionExpirationStore}
 *
 * @author Marcus da Coregio
 */
class SortedSetRedisSessionExpirationStoreTests {

	private SortedSetRedisSessionExpirationStore expirationStore;

	private final RedisTemplate<String, Object> redisTemplate = mock(Answers.RETURNS_DEEP_STUBS);

	@BeforeEach
	void setup() {
		this.expirationStore = new SortedSetRedisSessionExpirationStore(this.redisTemplate,
				RedisIndexedSessionRepository.DEFAULT_NAMESPACE);
	}

	@Test
	void setNamespaceWhenNullOrEmptyThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.expirationStore.setNamespace(null))
			.withMessage("namespace cannot be null or empty");
		assertThatIllegalArgumentException().isThrownBy(() -> this.expirationStore.setNamespace(""))
			.withMessage("namespace cannot be null or empty");
	}

	@Test
	void setClockWhenNullThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.expirationStore.setClock(null))
			.withMessage("clock cannot be null");
	}

	@Test
	void setCleanupCountWhenZeroOrNegativeThenException() {
		assertThatIllegalStateException().isThrownBy(() -> this.expirationStore.setCleanupCount(0))
			.withMessage("cleanupCount must be greater than 0");
		assertThatIllegalStateException().isThrownBy(() -> this.expirationStore.setCleanupCount(-1))
			.withMessage("cleanupCount must be greater than 0");
	}

	@Test
	void cleanupExpiredSessionsThenTouchExpiredSessions() {
		given(this.redisTemplate.opsForZSet()
			.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
			.willReturn(Set.of("1", "2", "3"));
		this.expirationStore.cleanupExpiredSessions();
		verify(this.redisTemplate).hasKey("spring:session:sessions:1");
		verify(this.redisTemplate).hasKey("spring:session:sessions:2");
		verify(this.redisTemplate).hasKey("spring:session:sessions:3");
	}

}
