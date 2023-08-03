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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.util.Assert;

/**
 * Uses a sorted set to store the expiration times for sessions. The score of each entry
 * is the expiration time of the session. The value is the session id.
 *
 * @author Marcus da Coregio
 */
final class SortedSetReactiveRedisSessionExpirationStore {

	private final ReactiveRedisOperations<String, Object> sessionRedisOperations;

	private String namespace;

	private int retrieveCount = 100;

	SortedSetReactiveRedisSessionExpirationStore(ReactiveRedisOperations<String, Object> sessionRedisOperations,
			String namespace) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations cannot be null");
		Assert.hasText(namespace, "namespace cannot be null or empty");
		this.sessionRedisOperations = sessionRedisOperations;
		this.namespace = namespace;
	}

	/**
	 * Add the session id associated with the expiration time into the sorted set.
	 * @param sessionId the session id
	 * @param expiration the expiration time
	 * @return a {@link Mono} that completes when the operation completes
	 */
	Mono<Void> add(String sessionId, Instant expiration) {
		long expirationInMillis = expiration.toEpochMilli();
		return this.sessionRedisOperations.opsForZSet().add(getExpirationsKey(), sessionId, expirationInMillis).then();
	}

	/**
	 * Remove the session id from the sorted set.
	 * @param sessionId the session id
	 * @return a {@link Mono} that completes when the operation completes
	 */
	Mono<Void> remove(String sessionId) {
		return this.sessionRedisOperations.opsForZSet().remove(getExpirationsKey(), sessionId).then();
	}

	/**
	 * Retrieve the session ids that have the expiration time less than the value passed
	 * in {@code expiredBefore}.
	 * @param expiredBefore the expiration time
	 * @return a {@link Flux} that emits the session ids
	 */
	Flux<String> retrieveExpiredSessions(Instant expiredBefore) {
		Range<Double> range = Range.closed(0D, (double) expiredBefore.toEpochMilli());
		Limit limit = Limit.limit().count(this.retrieveCount);
		return this.sessionRedisOperations.opsForZSet()
			.reverseRangeByScore(getExpirationsKey(), range, limit)
			.cast(String.class);
	}

	private String getExpirationsKey() {
		return this.namespace + "sessions:expirations";
	}

	/**
	 * Set the namespace for the keys used by this class.
	 * @param namespace the namespace
	 */
	void setNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be null or empty");
		this.namespace = namespace;
	}

}
