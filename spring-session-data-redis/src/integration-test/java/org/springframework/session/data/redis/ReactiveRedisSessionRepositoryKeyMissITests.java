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
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.spy;

/**
 * Key miss error tests for {@link ReactiveRedisSessionRepository}
 *
 * @author Marcus da Coregio
 * @see <a href="https://github.com/spring-projects/spring-session/issues/2021">Related
 * GitHub Issue</a>
 */
@ExtendWith(SpringExtension.class)
class ReactiveRedisSessionRepositoryKeyMissITests extends AbstractRedisITests {

	private ReactiveRedisSessionRepository sessionRepository;

	private ReactiveRedisOperations<String, Object> spyOperations;

	AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

	@Test
	void findByIdWhenSessionDeletedWhileSavingDeltaThenThrowIllegalStateException() {
		this.context.register(Config.class);
		refreshAndPrepareFields();
		RedisSession session = createAndSaveSession(Instant.now());
		session.setAttribute("new", "value");

		ReactiveHashOperations<String, Object, Object> opsForHash = spy(this.spyOperations.opsForHash());
		given(this.spyOperations.opsForHash()).willReturn(opsForHash);
		willAnswer((invocation) -> this.sessionRepository.deleteById(session.getId())
				.then((Mono<Void>) invocation.callRealMethod())).given(opsForHash).putAll(any(), any());

		this.sessionRepository.save(session).block();
		assertThatIllegalStateException().isThrownBy(() -> this.sessionRepository.findById(session.getId()).block())
				.withMessage("creationTime key must not be null");
	}

	@Test
	void findByIdWhenSessionDeletedWhileSavingDeltaAndSafeMapperThenSessionIsNull() {
		this.context.register(RedisSessionMapperConfig.class);
		refreshAndPrepareFields();
		RedisSession session = createAndSaveSession(Instant.now());
		session.setAttribute("new", "value");

		ReactiveHashOperations<String, Object, Object> opsForHash = spy(this.spyOperations.opsForHash());
		given(this.spyOperations.opsForHash()).willReturn(opsForHash);
		willAnswer((invocation) -> this.sessionRepository.deleteById(session.getId())
				.then((Mono<Void>) invocation.callRealMethod())).given(opsForHash).putAll(any(), any());

		this.sessionRepository.save(session).block();
		assertThat(this.sessionRepository.findById(session.getId()).block()).isNull();
	}

	@SuppressWarnings("unchecked")
	private void refreshAndPrepareFields() {
		this.context.refresh();
		this.sessionRepository = this.context.getBean(ReactiveRedisSessionRepository.class);
		ReactiveRedisOperations<String, Object> redisOperations = (ReactiveRedisOperations<String, Object>) ReflectionTestUtils
				.getField(this.sessionRepository, "sessionRedisOperations");
		this.spyOperations = spy(redisOperations);
		ReflectionTestUtils.setField(this.sessionRepository, "sessionRedisOperations", this.spyOperations);
	}

	private RedisSession createAndSaveSession(Instant lastAccessedTime) {
		RedisSession session = this.sessionRepository.createSession().block();
		session.setLastAccessedTime(lastAccessedTime);
		session.setAttribute("attribute1", "value1");
		this.sessionRepository.save(session).block();
		return this.sessionRepository.findById(session.getId()).block();
	}

	@Configuration
	@EnableRedisWebSession
	static class Config extends BaseConfig {

	}

	@Configuration
	@EnableRedisWebSession
	static class RedisSessionMapperConfig extends BaseConfig {

		@Bean
		ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository> redisSessionRepositoryCustomizer() {
			return (redisSessionRepository) -> redisSessionRepository
					.setRedisSessionMapper(new SafeRedisSessionMapper(redisSessionRepository));
		}

	}

	static class SafeRedisSessionMapper implements BiFunction<String, Map<String, Object>, Mono<MapSession>> {

		private final RedisSessionMapper delegate = new RedisSessionMapper();

		private final ReactiveRedisSessionRepository sessionRepository;

		SafeRedisSessionMapper(ReactiveRedisSessionRepository sessionRepository) {
			this.sessionRepository = sessionRepository;
		}

		@Override
		public Mono<MapSession> apply(String sessionId, Map<String, Object> map) {
			return Mono.fromSupplier(() -> this.delegate.apply(sessionId, map)).onErrorResume(
					IllegalStateException.class,
					(ex) -> this.sessionRepository.deleteById(sessionId).then(Mono.empty()));
		}

	}

}
