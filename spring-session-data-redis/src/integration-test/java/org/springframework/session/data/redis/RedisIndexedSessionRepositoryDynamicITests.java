/*
 * Copyright 2014-2025 the original author or authors.
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;

/**
 * Key miss error tests for {@link RedisIndexedSessionRepository}
 *
 * @author Marcus da Coregio
 * @see <a href="https://github.com/spring-projects/spring-session/issues/2021">Related
 * GitHub Issue</a>
 */
@SpringJUnitConfig
class RedisIndexedSessionRepositoryDynamicITests extends AbstractRedisITests {

	private RedisIndexedSessionRepository sessionRepository;

	private RedisOperations<String, Object> spyOperations;

	AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

	@Test
	void findByIdWhenSessionDeletedWhileSavingDeltaThenThrowIllegalStateException() {
		this.context.register(Config.class);
		refreshAndPrepareFields();
		RedisSession session = createAndSaveSession(Instant.now());
		session.setAttribute("new", "value");

		BoundHashOperations<String, Object, Object> opsForHash = spy(this.spyOperations.boundHashOps(anyString()));
		given(this.spyOperations.boundHashOps(anyString())).willReturn(opsForHash);

		this.sessionRepository.save(session);
		assertThatIllegalStateException().isThrownBy(() -> this.sessionRepository.findById(session.getId()))
			.withMessage("creationTime key must not be null");
	}

	@Test
	void findByIdWhenSessionDeletedWhileSavingDeltaAndSafeMapperThenSessionIsNull() {
		this.context.register(RedisSessionMapperConfig.class);
		refreshAndPrepareFields();
		RedisSession session = createAndSaveSession(Instant.now());
		session.setAttribute("new", "value");

		BoundHashOperations<String, Object, Object> opsForHash = spy(this.spyOperations.boundHashOps(anyString()));
		given(this.spyOperations.boundHashOps(anyString())).willReturn(opsForHash);

		this.sessionRepository.save(session);
		assertThat(this.sessionRepository.findById(session.getId())).isNull();
	}

	@SuppressWarnings("unchecked")
	private void refreshAndPrepareFields() {
		this.context.refresh();
		this.sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) ReflectionTestUtils
			.getField(this.sessionRepository, "sessionRedisOperations");
		this.spyOperations = spy(redisOperations);
		ReflectionTestUtils.setField(this.sessionRepository, "sessionRedisOperations", this.spyOperations);
	}

	private RedisSession createAndSaveSession(Instant lastAccessedTime) {
		RedisSession session = this.sessionRepository.createSession();
		session.setLastAccessedTime(lastAccessedTime);
		session.setAttribute("attribute1", "value1");
		this.sessionRepository.save(session);
		return this.sessionRepository.findById(session.getId());
	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class Config extends BaseConfig {

	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class RedisSessionMapperConfig extends BaseConfig {

		@Bean
		SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisSessionRepositoryCustomizer() {
			return (redisSessionRepository) -> redisSessionRepository
				.setRedisSessionMapper(new SafeRedisSessionMapper(redisSessionRepository.getSessionRedisOperations()));
		}

	}

	static class SafeRedisSessionMapper implements BiFunction<String, Map<String, Object>, MapSession> {

		private final RedisSessionMapper delegate = new RedisSessionMapper();

		private final RedisOperations<String, Object> redisOperations;

		SafeRedisSessionMapper(RedisOperations<String, Object> redisOperations) {
			this.redisOperations = redisOperations;
		}

		@Override
		public MapSession apply(String sessionId, Map<String, Object> map) {
			try {
				return this.delegate.apply(sessionId, map);
			}
			catch (IllegalStateException ex) {
				this.redisOperations.delete("spring:session:sessions:" + sessionId);
				return null;
			}
		}

	}

}
