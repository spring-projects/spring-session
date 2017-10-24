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

package org.springframework.session.data.redis.config.annotation.web.server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.session.data.redis.ReactiveRedisOperationsSessionRepository;
import org.springframework.session.data.redis.RedisFlushMode;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RedisWebSessionConfiguration}.
 *
 * @author Vedran Pavic
 */
public class RedisWebSessionConfigurationTests {

	private static final String REDIS_NAMESPACE = "testNamespace";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private AnnotationConfigApplicationContext context;

	@Before
	public void before() {
		this.context = new AnnotationConfigApplicationContext();
	}

	@After
	public void after() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(RedisConfiguration.class, DefaultConfiguration.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
	}

	@Test
	public void customNamespace() {
		registerAndRefresh(RedisConfiguration.class, CustomNamespaceConfiguration.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "keyPrefix"))
				.isEqualTo("spring:session:" + REDIS_NAMESPACE + ":");
	}

	@Test
	public void customMaxInactiveInterval() {
		registerAndRefresh(RedisConfiguration.class,
				CustomMaxInactiveIntervalConfiguration.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customFlushMode() {
		registerAndRefresh(RedisConfiguration.class, CustomFlushModeConfiguration.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "redisFlushMode"))
				.isEqualTo(RedisFlushMode.IMMEDIATE);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	static class RedisConfiguration {

		@Bean
		public ReactiveRedisConnectionFactory redisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@Configuration
	@EnableRedisWebSession
	static class DefaultConfiguration {

	}

	@Configuration
	@EnableRedisWebSession(redisNamespace = REDIS_NAMESPACE)
	static class CustomNamespaceConfiguration {

	}

	@Configuration
	@EnableRedisWebSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalConfiguration {

	}

	@Configuration
	@EnableRedisWebSession(redisFlushMode = RedisFlushMode.IMMEDIATE)
	static class CustomFlushModeConfiguration {

	}

}
