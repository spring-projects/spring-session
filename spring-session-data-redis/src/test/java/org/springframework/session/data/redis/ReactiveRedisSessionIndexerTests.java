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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.IndexResolver;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SingleIndexResolver;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository.RedisSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ReactiveRedisSessionIndexer}.
 *
 * @author Marcus da Coregio
 */
class ReactiveRedisSessionIndexerTests {

	ReactiveRedisSessionIndexer indexer;

	ReactiveRedisOperations<String, Object> sessionRedisOperations = mock(Answers.RETURNS_DEEP_STUBS);

	String indexKeyPrefix = "spring:session:sessions:index:";

	@BeforeEach
	void setup() {
		this.indexer = new ReactiveRedisSessionIndexer(this.sessionRedisOperations, "spring:session:");
	}

	@Test
	void getIndexesWhenIndexKeyExistsThenReturnsIndexNameAndValue() {
		List<Object> indexKeys = List.of(this.indexKeyPrefix + "principalName:user",
				this.indexKeyPrefix + "index_name:index_value");
		given(this.sessionRedisOperations.opsForSet().members(anyString())).willReturn(Flux.fromIterable(indexKeys));
		Map<String, String> indexes = this.indexer.getIndexes("1234").block();
		assertThat(indexes).hasSize(2)
			.containsEntry("principalName", "user")
			.containsEntry("index_name", "index_value");
	}

	@Test
	void deleteWhenSessionIdHasIndexesThenRemoveSessionIdFromIndexesAndDeleteSessionIndexKey() {
		String index1 = this.indexKeyPrefix + "principalName:user";
		String index2 = this.indexKeyPrefix + "index_name:index_value";
		List<Object> indexKeys = List.of(index1, index2);
		given(this.sessionRedisOperations.opsForSet().members(anyString())).willReturn(Flux.fromIterable(indexKeys));
		given(this.sessionRedisOperations.delete(anyString())).willReturn(Mono.just(1L));
		given(this.sessionRedisOperations.opsForSet().remove(anyString(), anyString())).willReturn(Mono.just(1L));
		this.indexer.delete("1234").block();
		verify(this.sessionRedisOperations).delete("spring:session:sessions:1234:idx");
		verify(this.sessionRedisOperations.opsForSet()).remove(index1, "1234");
		verify(this.sessionRedisOperations.opsForSet()).remove(index2, "1234");
	}

	@Test
	void updateWhenSessionHasNoIndexesSavedThenUpdates() {
		RedisSession session = mock();
		given(session.getAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
			.willReturn("user");
		given(session.getId()).willReturn("1234");
		given(this.sessionRedisOperations.opsForSet().members(anyString())).willReturn(Flux.empty());
		given(this.sessionRedisOperations.opsForSet().add(anyString(), anyString())).willReturn(Mono.just(1L));
		this.indexer.update(session).block();
		verify(this.sessionRedisOperations.opsForSet()).add(this.indexKeyPrefix + "PRINCIPAL_NAME_INDEX_NAME:user",
				"1234");
	}

	@Test
	void updateWhenSessionIndexesSavedWithSameValueThenDoesNotUpdate() {
		String indexKey = this.indexKeyPrefix + ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
				+ ":user";
		RedisSession session = mock();
		given(session.getAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
			.willReturn("user");
		given(session.getId()).willReturn("1234");
		given(this.sessionRedisOperations.opsForSet().members(anyString()))
			.willReturn(Flux.fromIterable(List.of(indexKey)));
		this.indexer.update(session).block();
		verify(this.sessionRedisOperations.opsForSet(), never()).add(anyString(), anyString());
		verify(this.sessionRedisOperations.opsForSet(), never()).remove(anyString(), anyString());
	}

	@Test
	void updateWhenSessionIndexesSavedWithDifferentValueThenUpdates() {
		String indexKey = this.indexKeyPrefix + ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
				+ ":user";
		RedisSession session = mock();
		given(session.getAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
			.willReturn("newuser");
		given(session.getId()).willReturn("1234");
		given(this.sessionRedisOperations.opsForSet().members(anyString()))
			.willReturn(Flux.fromIterable(List.of(indexKey)));
		given(this.sessionRedisOperations.opsForSet().add(anyString(), anyString())).willReturn(Mono.just(1L));
		given(this.sessionRedisOperations.opsForSet().remove(anyString(), anyString())).willReturn(Mono.just(1L));
		this.indexer.update(session).block();
		verify(this.sessionRedisOperations.opsForSet()).add(
				this.indexKeyPrefix + ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":newuser",
				"1234");
		verify(this.sessionRedisOperations.opsForSet()).remove(indexKey, "1234");
	}

	@Test
	void updateWhenMultipleIndexResolvedThenUpdated() {
		IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(
				new PrincipalNameIndexResolver<>(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME),
				new TestIndexResolver<>("test"));
		this.indexer.setIndexResolver(indexResolver);
		RedisSession session = mock();
		given(session.getAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
			.willReturn("user");
		given(session.getAttribute("test")).willReturn("testvalue");
		given(session.getId()).willReturn("1234");
		given(this.sessionRedisOperations.opsForSet().members(anyString())).willReturn(Flux.empty());
		given(this.sessionRedisOperations.opsForSet().add(anyString(), anyString())).willReturn(Mono.just(1L));
		this.indexer.update(session).block();
		verify(this.sessionRedisOperations.opsForSet()).add(this.indexKeyPrefix + "PRINCIPAL_NAME_INDEX_NAME:user",
				"1234");
		verify(this.sessionRedisOperations.opsForSet()).add(this.indexKeyPrefix + "test:testvalue", "1234");
	}

	@Test
	void setNamespaceShouldUpdateIndexKeyPrefix() {
		String originalPrefix = (String) ReflectionTestUtils.getField(this.indexer, "indexKeyPrefix");
		this.indexer.setNamespace("my:namespace:");
		String updatedPrefix = (String) ReflectionTestUtils.getField(this.indexer, "indexKeyPrefix");
		assertThat(originalPrefix).isEqualTo(this.indexKeyPrefix);
		assertThat(updatedPrefix).isEqualTo("my:namespace:sessions:index:");
	}

	@Test
	void constructorWhenSessionRedisOperationsNullThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReactiveRedisSessionIndexer(null, "spring:session:"))
			.withMessage("sessionRedisOperations cannot be null");
	}

	@Test
	void constructorWhenNamespaceNullThenException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ReactiveRedisSessionIndexer(this.sessionRedisOperations, null))
			.withMessage("namespace cannot be empty");
	}

	@Test
	void constructorWhenNamespaceEmptyThenException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ReactiveRedisSessionIndexer(this.sessionRedisOperations, ""))
			.withMessage("namespace cannot be empty");
	}

	static class TestIndexResolver<S extends Session> extends SingleIndexResolver<S> {

		protected TestIndexResolver(String indexName) {
			super(indexName);
		}

		@Override
		public String resolveIndexValueFor(S session) {
			return session.getAttribute(getIndexName());
		}

	}

}
