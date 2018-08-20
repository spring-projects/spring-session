/*
 * Copyright 2014-2018 the original author or authors.
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

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.ReactiveRedisOperationsSessionRepository;
import org.springframework.session.data.redis.RedisFlushMode;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
		registerAndRefresh(RedisConfig.class, DefaultConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
	}

	@Test
	public void springSessionRedisOperationsResolvingConfiguration() {
		registerAndRefresh(RedisConfig.class,
				SpringSessionRedisOperationsResolvingConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		ReactiveRedisOperations<String, Object> springSessionRedisOperations = this.context
				.getBean(SpringSessionRedisOperationsResolvingConfig.class)
				.getSpringSessionRedisOperations();
		assertThat(springSessionRedisOperations).isNotNull();
		assertThat((ReactiveRedisOperations) ReflectionTestUtils.getField(repository,
				"sessionRedisOperations")).isEqualTo(springSessionRedisOperations);
	}

	@Test
	public void customNamespace() {
		registerAndRefresh(RedisConfig.class, CustomNamespaceConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "namespace"))
				.isEqualTo(REDIS_NAMESPACE + ":");
	}

	@Test
	public void customMaxInactiveInterval() {
		registerAndRefresh(RedisConfig.class, CustomMaxInactiveIntervalConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customFlushMode() {
		registerAndRefresh(RedisConfig.class, CustomFlushModeConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "redisFlushMode"))
				.isEqualTo(RedisFlushMode.IMMEDIATE);
	}

	@Test
	public void qualifiedConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class,
				QualifiedConnectionFactoryRedisConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		ReactiveRedisConnectionFactory redisConnectionFactory = this.context.getBean(
				"qualifiedRedisConnectionFactory", ReactiveRedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		ReactiveRedisOperations redisOperations = (ReactiveRedisOperations) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	public void primaryConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, PrimaryConnectionFactoryRedisConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		ReactiveRedisConnectionFactory redisConnectionFactory = this.context.getBean(
				"primaryRedisConnectionFactory", ReactiveRedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		ReactiveRedisOperations redisOperations = (ReactiveRedisOperations) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	public void qualifiedAndPrimaryConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class,
				QualifiedAndPrimaryConnectionFactoryRedisConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		ReactiveRedisConnectionFactory redisConnectionFactory = this.context.getBean(
				"qualifiedRedisConnectionFactory", ReactiveRedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		ReactiveRedisOperations redisOperations = (ReactiveRedisOperations) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	public void namedConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, NamedConnectionFactoryRedisConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		ReactiveRedisConnectionFactory redisConnectionFactory = this.context
				.getBean("redisConnectionFactory", ReactiveRedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		ReactiveRedisOperations redisOperations = (ReactiveRedisOperations) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	public void multipleConnectionFactoryRedisConfig() {
		assertThatThrownBy(() -> registerAndRefresh(RedisConfig.class,
				MultipleConnectionFactoryRedisConfig.class))
						.isInstanceOf(BeanCreationException.class)
						.hasMessageContaining("expected single matching bean but found 2");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void customRedisSerializerConfig() {
		registerAndRefresh(RedisConfig.class, CustomRedisSerializerConfig.class);

		ReactiveRedisOperationsSessionRepository repository = this.context
				.getBean(ReactiveRedisOperationsSessionRepository.class);
		RedisSerializer<Object> redisSerializer = this.context
				.getBean("springSessionDefaultRedisSerializer", RedisSerializer.class);
		assertThat(repository).isNotNull();
		assertThat(redisSerializer).isNotNull();
		ReactiveRedisOperations redisOperations = (ReactiveRedisOperations) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		RedisSerializationContext serializationContext = redisOperations
				.getSerializationContext();
		assertThat(ReflectionTestUtils.getField(
				serializationContext.getValueSerializationPair().getReader(),
				"serializer")).isEqualTo(redisSerializer);
		assertThat(ReflectionTestUtils.getField(
				serializationContext.getValueSerializationPair().getWriter(),
				"serializer")).isEqualTo(redisSerializer);
		assertThat(ReflectionTestUtils.getField(
				serializationContext.getHashValueSerializationPair().getReader(),
				"serializer")).isEqualTo(redisSerializer);
		assertThat(ReflectionTestUtils.getField(
				serializationContext.getHashValueSerializationPair().getWriter(),
				"serializer")).isEqualTo(redisSerializer);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	static class RedisConfig {

		@Bean
		public ReactiveRedisConnectionFactory defaultRedisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@EnableRedisWebSession
	static class DefaultConfig {

	}

	@EnableRedisWebSession
	static class SpringSessionRedisOperationsResolvingConfig {

		@SpringSessionRedisOperations
		private ReactiveRedisOperations<String, Object> springSessionRedisOperations;

		public ReactiveRedisOperations<String, Object> getSpringSessionRedisOperations() {
			return this.springSessionRedisOperations;
		}

	}

	@EnableRedisWebSession(redisNamespace = REDIS_NAMESPACE)
	static class CustomNamespaceConfig {

	}

	@EnableRedisWebSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalConfig {

	}

	@EnableRedisWebSession(redisFlushMode = RedisFlushMode.IMMEDIATE)
	static class CustomFlushModeConfig {

	}

	@EnableRedisWebSession
	static class QualifiedConnectionFactoryRedisConfig {

		@Bean
		@SpringSessionRedisConnectionFactory
		public ReactiveRedisConnectionFactory qualifiedRedisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@EnableRedisWebSession
	static class PrimaryConnectionFactoryRedisConfig {

		@Bean
		@Primary
		public ReactiveRedisConnectionFactory primaryRedisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@EnableRedisWebSession
	static class QualifiedAndPrimaryConnectionFactoryRedisConfig {

		@Bean
		@SpringSessionRedisConnectionFactory
		public ReactiveRedisConnectionFactory qualifiedRedisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

		@Bean
		@Primary
		public ReactiveRedisConnectionFactory primaryRedisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@EnableRedisWebSession
	static class NamedConnectionFactoryRedisConfig {

		@Bean
		public ReactiveRedisConnectionFactory redisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@EnableRedisWebSession
	static class MultipleConnectionFactoryRedisConfig {

		@Bean
		public ReactiveRedisConnectionFactory secondaryRedisConnectionFactory() {
			return mock(ReactiveRedisConnectionFactory.class);
		}

	}

	@EnableRedisWebSession
	static class CustomRedisSerializerConfig {

		@Bean
		@SuppressWarnings("unchecked")
		public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
			return mock(RedisSerializer.class);
		}

	}

}
