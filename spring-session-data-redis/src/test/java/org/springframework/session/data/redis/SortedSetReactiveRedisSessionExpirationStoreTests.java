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

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
class SortedSetReactiveRedisSessionExpirationStoreTests {

	SortedSetReactiveRedisSessionExpirationStore store;

	ReactiveRedisOperations<String, Object> sessionRedisOperations = mock(Answers.RETURNS_DEEP_STUBS);

	String namespace = "spring:session:";

	@BeforeEach
	void setup() {
		this.store = new SortedSetReactiveRedisSessionExpirationStore(this.sessionRedisOperations, this.namespace);
		given(this.sessionRedisOperations.opsForZSet().add(anyString(), anyString(), anyDouble()))
			.willReturn(Mono.empty());
		given(this.sessionRedisOperations.opsForZSet().remove(anyString(), anyString())).willReturn(Mono.empty());
		given(this.sessionRedisOperations.opsForZSet()
			.reverseRangeByScore(anyString(), any(Range.class), any(Limit.class))).willReturn(Flux.empty());
	}

	@Test
	void addThenStoresSessionIdRankedByExpireAtEpochMilli() {
		String sessionId = "1234";
		Instant expireAt = Instant.ofEpochMilli(1702314490000L);
		StepVerifier.create(this.store.add(sessionId, expireAt)).verifyComplete();
		verify(this.sessionRedisOperations.opsForZSet()).add(this.namespace + "sessions:expirations", sessionId,
				expireAt.toEpochMilli());
	}

	@Test
	void removeThenRemovesSessionIdFromSortedSet() {
		String sessionId = "1234";
		StepVerifier.create(this.store.remove(sessionId)).verifyComplete();
		verify(this.sessionRedisOperations.opsForZSet()).remove(this.namespace + "sessions:expirations", sessionId);
	}

	@Test
	void retrieveExpiredSessionsThenUsesExpectedRangeAndLimit() {
		Instant now = Instant.now();
		StepVerifier.create(this.store.retrieveExpiredSessions(now)).verifyComplete();
		ArgumentCaptor<Range<Double>> rangeCaptor = ArgumentCaptor.forClass(Range.class);
		ArgumentCaptor<Limit> limitCaptor = ArgumentCaptor.forClass(Limit.class);
		verify(this.sessionRedisOperations.opsForZSet()).reverseRangeByScore(
				eq(this.namespace + "sessions:expirations"), rangeCaptor.capture(), limitCaptor.capture());
		assertThat(rangeCaptor.getValue().getLowerBound().getValue()).hasValue(0D);
		assertThat(rangeCaptor.getValue().getUpperBound().getValue()).hasValue((double) now.toEpochMilli());
		assertThat(limitCaptor.getValue().getCount()).isEqualTo(100);
		assertThat(limitCaptor.getValue().getOffset()).isEqualTo(0);
	}

}
