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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SortedSetRedisSessionExpirationStore}
 *
 * @author Marcus da Coregio
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SortedSetRedisSessionExpirationStoreITests.Config.class)
@WebAppConfiguration
class SortedSetRedisSessionExpirationStoreITests {

	@Autowired
	private SortedSetRedisSessionExpirationStore expirationStore;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private static final Instant mockedTime = LocalDateTime.of(2024, 5, 8, 10, 30, 0)
		.atZone(ZoneOffset.UTC)
		.toInstant();

	private static final Clock clock;

	static {
		clock = Clock.fixed(mockedTime, ZoneOffset.UTC);
	}

	@Test
	void saveThenStoreSessionWithItsExpiration() {
		Instant expireAt = mockedTime.plusSeconds(5);
		RedisSession session = createSession("123", expireAt);
		this.expirationStore.save(session);
		Double score = this.redisTemplate.opsForZSet().score("spring:session:sessions:expirations", "123");
		assertThat(score).isEqualTo(expireAt.toEpochMilli());
	}

	@Test
	void removeWhenSessionIdExistsThenRemoved() {
		RedisSession session = createSession("toBeRemoved", mockedTime);
		this.expirationStore.save(session);
		Double score = this.redisTemplate.opsForZSet().score("spring:session:sessions:expirations", "toBeRemoved");
		assertThat(score).isEqualTo(mockedTime.toEpochMilli());
		this.expirationStore.remove("toBeRemoved");
		score = this.redisTemplate.opsForZSet().score("spring:session:sessions:expirations", "toBeRemoved");
		assertThat(score).isNull();
	}

	private RedisSession createSession(String sessionId, Instant expireAt) {
		RedisSession session = mock();
		given(session.getId()).willReturn(sessionId);
		given(session.getLastAccessedTime()).willReturn(expireAt);
		given(session.getMaxInactiveInterval()).willReturn(Duration.ZERO);
		return session;
	}

	@Configuration(proxyBeanMethods = false)
	@Import(AbstractRedisITests.BaseConfig.class)
	static class Config {

		@Bean
		RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
			RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
			redisTemplate.setKeySerializer(RedisSerializer.string());
			redisTemplate.setHashKeySerializer(RedisSerializer.string());
			redisTemplate.setConnectionFactory(redisConnectionFactory);
			return redisTemplate;
		}

		@Bean
		RedisSessionExpirationStore redisSessionExpirationStore(RedisTemplate<String, Object> redisTemplate) {
			SortedSetRedisSessionExpirationStore store = new SortedSetRedisSessionExpirationStore(redisTemplate,
					RedisIndexedSessionRepository.DEFAULT_NAMESPACE);
			store.setClock(SortedSetRedisSessionExpirationStoreITests.clock);
			return store;
		}

	}

}
