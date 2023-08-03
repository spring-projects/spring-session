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

import java.util.HashMap;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.IndexResolver;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository.RedisSession;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Uses an {@link IndexResolver} to keep track of the indexes for a
 * {@link ReactiveRedisIndexedSessionRepository.RedisSession}. Only updates indexes that
 * have changed.
 *
 * @author Marcus da Coregio
 */
final class ReactiveRedisSessionIndexer {

	private final ReactiveRedisOperations<String, Object> sessionRedisOperations;

	private String namespace;

	private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(
			new PrincipalNameIndexResolver<>(ReactiveRedisIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME));

	private String indexKeyPrefix;

	ReactiveRedisSessionIndexer(ReactiveRedisOperations<String, Object> sessionRedisOperations, String namespace) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations cannot be null");
		Assert.hasText(namespace, "namespace cannot be empty");
		this.sessionRedisOperations = sessionRedisOperations;
		this.namespace = namespace;
		updateIndexKeyPrefix();
	}

	Mono<Void> update(RedisSession redisSession) {
		return getIndexes(redisSession.getId()).map((originalIndexes) -> {
			Map<String, String> indexes = this.indexResolver.resolveIndexesFor(redisSession);
			Map<String, String> indexToDelete = new HashMap<>();
			Map<String, String> indexToAdd = new HashMap<>();
			for (Map.Entry<String, String> entry : indexes.entrySet()) {
				if (!originalIndexes.containsKey(entry.getKey())) {
					indexToAdd.put(entry.getKey(), entry.getValue());
					continue;
				}
				if (!originalIndexes.get(entry.getKey()).equals(entry.getValue())) {
					indexToDelete.put(entry.getKey(), originalIndexes.get(entry.getKey()));
					indexToAdd.put(entry.getKey(), entry.getValue());
				}
			}
			if (CollectionUtils.isEmpty(indexes) && !CollectionUtils.isEmpty(originalIndexes)) {
				indexToDelete.putAll(originalIndexes);
			}
			return Tuples.of(indexToDelete, indexToAdd);
		}).flatMap((indexes) -> updateIndexes(indexes.getT1(), indexes.getT2(), redisSession.getId()));
	}

	private Mono<Void> updateIndexes(Map<String, String> indexToDelete, Map<String, String> indexToAdd,
			String sessionId) {
		// @formatter:off
		return Flux.fromIterable(indexToDelete.entrySet())
			.flatMap((entry) -> {
				String indexKey = getIndexKey(entry.getKey(), entry.getValue());
				return removeSessionFromIndex(indexKey, sessionId).thenReturn(indexKey);
			})
			.flatMap((indexKey) -> this.sessionRedisOperations.opsForSet().remove(getSessionIndexesKey(sessionId), indexKey))
			.thenMany(Flux.fromIterable(indexToAdd.entrySet()))
			.flatMap((entry) -> {
				String indexKey = getIndexKey(entry.getKey(), entry.getValue());
				return this.sessionRedisOperations.opsForSet().add(indexKey, sessionId).thenReturn(indexKey);
			})
			.flatMap((indexKey) -> this.sessionRedisOperations.opsForSet().add(getSessionIndexesKey(sessionId), indexKey))
			.then();
		// @formatter:on
	}

	Mono<Void> delete(String sessionId) {
		String sessionIndexesKey = getSessionIndexesKey(sessionId);
		return this.sessionRedisOperations.opsForSet()
			.members(sessionIndexesKey)
			.flatMap((indexKey) -> removeSessionFromIndex((String) indexKey, sessionId))
			.then(this.sessionRedisOperations.delete(sessionIndexesKey))
			.then();
	}

	private Mono<Void> removeSessionFromIndex(String indexKey, String sessionId) {
		return this.sessionRedisOperations.opsForSet().remove(indexKey, sessionId).then();
	}

	Mono<Map<String, String>> getIndexes(String sessionId) {
		String sessionIndexesKey = getSessionIndexesKey(sessionId);
		return this.sessionRedisOperations.opsForSet()
			.members(sessionIndexesKey)
			.cast(String.class)
			.collectMap((indexKey) -> indexKey.substring(this.indexKeyPrefix.length()).split(":")[0],
					(indexKey) -> indexKey.substring(this.indexKeyPrefix.length()).split(":")[1]);
	}

	Flux<String> getSessionIds(String indexName, String indexValue) {
		String indexKey = getIndexKey(indexName, indexValue);
		return this.sessionRedisOperations.opsForSet().members(indexKey).cast(String.class);
	}

	private void updateIndexKeyPrefix() {
		this.indexKeyPrefix = this.namespace + "sessions:index:";
	}

	private String getSessionIndexesKey(String sessionId) {
		return this.namespace + "sessions:" + sessionId + ":idx";
	}

	private String getIndexKey(String indexName, String indexValue) {
		return this.indexKeyPrefix + indexName + ":" + indexValue;
	}

	void setNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be empty");
		this.namespace = namespace;
		updateIndexKeyPrefix();
	}

	void setIndexResolver(IndexResolver<Session> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver cannot be null");
		this.indexResolver = indexResolver;
	}

}
