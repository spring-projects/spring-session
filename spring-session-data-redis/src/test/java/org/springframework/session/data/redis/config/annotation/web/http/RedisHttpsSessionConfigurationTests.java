/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.session.data.redis.config.annotation.web.http;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RedisHttpSessionConfiguration}.
 *
 * @author Eddú Meléndez
 * @author Mark Paluch
 * @author Vedran Pavic
 */
class RedisHttpsSessionConfigurationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private AnnotationConfigApplicationContext context;

	@BeforeEach
	void before() {
		this.context = new AnnotationConfigApplicationContext();
	}

	@AfterEach
	void after() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void resolveValue() {
		registerAndRefresh(RedisConfig.class, CustomRedisHttpSessionConfiguration.class);
		RedisHttpSessionConfiguration configuration = this.context.getBean(RedisHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "redisNamespace")).isEqualTo("myRedisNamespace");
	}

	@Test
	void resolveValueByPlaceholder() {
		this.context
				.setEnvironment(new MockEnvironment().withProperty("session.redis.namespace", "customRedisNamespace"));
		registerAndRefresh(RedisConfig.class, PropertySourceConfiguration.class,
				CustomRedisHttpSessionConfiguration2.class);
		RedisHttpSessionConfiguration configuration = this.context.getBean(RedisHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "redisNamespace")).isEqualTo("customRedisNamespace");
	}

	@Test
	void customFlushImmediately() {
		registerAndRefresh(RedisConfig.class, CustomFlushImmediatelyConfiguration.class);
		RedisSessionRepository sessionRepository = this.context.getBean(RedisSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
		assertThat(ReflectionTestUtils.getField(sessionRepository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void setCustomFlushImmediately() {
		registerAndRefresh(RedisConfig.class, CustomFlushImmediatelySetConfiguration.class);
		RedisSessionRepository sessionRepository = this.context.getBean(RedisSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
		assertThat(ReflectionTestUtils.getField(sessionRepository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void customSaveModeAnnotation() {
		registerAndRefresh(RedisConfig.class, CustomSaveModeExpressionAnnotationConfiguration.class);
		assertThat(this.context.getBean(RedisSessionRepository.class)).hasFieldOrPropertyWithValue("saveMode",
				SaveMode.ALWAYS);
	}

	@Test
	void qualifiedConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, QualifiedConnectionFactoryRedisConfig.class);

		RedisSessionRepository repository = this.context.getBean(RedisSessionRepository.class);
		RedisConnectionFactory redisConnectionFactory = this.context.getBean("qualifiedRedisConnectionFactory",
				RedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		@SuppressWarnings("unchecked")
		RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	void primaryConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, PrimaryConnectionFactoryRedisConfig.class);

		RedisSessionRepository repository = this.context.getBean(RedisSessionRepository.class);
		RedisConnectionFactory redisConnectionFactory = this.context.getBean("primaryRedisConnectionFactory",
				RedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		@SuppressWarnings("unchecked")
		RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	void qualifiedAndPrimaryConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, QualifiedAndPrimaryConnectionFactoryRedisConfig.class);

		RedisSessionRepository repository = this.context.getBean(RedisSessionRepository.class);
		RedisConnectionFactory redisConnectionFactory = this.context.getBean("qualifiedRedisConnectionFactory",
				RedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		@SuppressWarnings("unchecked")
		RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	void namedConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, NamedConnectionFactoryRedisConfig.class);

		RedisSessionRepository repository = this.context.getBean(RedisSessionRepository.class);
		RedisConnectionFactory redisConnectionFactory = this.context.getBean("redisConnectionFactory",
				RedisConnectionFactory.class);
		assertThat(repository).isNotNull();
		assertThat(redisConnectionFactory).isNotNull();
		@SuppressWarnings("unchecked")
		RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) ReflectionTestUtils
				.getField(repository, "sessionRedisOperations");
		assertThat(redisOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(redisOperations, "connectionFactory"))
				.isEqualTo(redisConnectionFactory);
	}

	@Test
	void multipleConnectionFactoryRedisConfig() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(RedisConfig.class, MultipleConnectionFactoryRedisConfig.class))
				.havingRootCause().withMessageContaining("expected single matching bean but found 2");
	}

	@Test
	void sessionRepositoryCustomizer() {
		registerAndRefresh(RedisConfig.class, SessionRepositoryCustomizerConfiguration.class);
		RedisSessionRepository sessionRepository = this.context.getBean(RedisSessionRepository.class);
		assertThat(sessionRepository).hasFieldOrPropertyWithValue("defaultMaxInactiveInterval",
				Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	private static RedisConnectionFactory mockRedisConnectionFactory() {
		RedisConnectionFactory connectionFactoryMock = mock(RedisConnectionFactory.class);
		RedisConnection connectionMock = mock(RedisConnection.class);
		RedisServerCommands commandsMock = mock(RedisServerCommands.class);
		given(connectionFactoryMock.getConnection()).willReturn(connectionMock);
		given(connectionMock.serverCommands()).willReturn(commandsMock);

		Properties keyspaceEventsConfig = new Properties();
		keyspaceEventsConfig.put("notify-keyspace-events", "KEA");
		given(commandsMock.getConfig("notify-keyspace-events")).willReturn(keyspaceEventsConfig);

		willAnswer((it) -> {
			SubscriptionListener listener = it.getArgument(0);
			listener.onPatternSubscribed(it.getArgument(1), 0);
			listener.onChannelSubscribed("__keyevent@0__:del".getBytes(), 0);
			listener.onChannelSubscribed("__keyevent@0__:expired".getBytes(), 0);

			return null;
		}).given(connectionMock).pSubscribe(any(), any());

		return connectionFactoryMock;
	}

	@Configuration
	static class PropertySourceConfiguration {

		@Bean
		PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}

	@Configuration
	static class RedisConfig {

		@Bean
		RedisConnectionFactory defaultRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	static class CustomFlushImmediatelySetConfiguration extends RedisHttpSessionConfiguration {

		CustomFlushImmediatelySetConfiguration() {
			setFlushMode(FlushMode.IMMEDIATE);
		}

	}

	@Configuration
	@EnableRedisHttpSession(flushMode = FlushMode.IMMEDIATE)
	static class CustomFlushImmediatelyConfiguration {

	}

	@EnableRedisHttpSession(saveMode = SaveMode.ALWAYS)
	static class CustomSaveModeExpressionAnnotationConfiguration {

	}

	@Configuration
	@EnableRedisHttpSession
	static class QualifiedConnectionFactoryRedisConfig {

		@Bean
		@SpringSessionRedisConnectionFactory
		RedisConnectionFactory qualifiedRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisHttpSession
	static class PrimaryConnectionFactoryRedisConfig {

		@Bean
		@Primary
		RedisConnectionFactory primaryRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisHttpSession
	static class QualifiedAndPrimaryConnectionFactoryRedisConfig {

		@Bean
		@SpringSessionRedisConnectionFactory
		RedisConnectionFactory qualifiedRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

		@Bean
		@Primary
		RedisConnectionFactory primaryRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisHttpSession
	static class NamedConnectionFactoryRedisConfig {

		@Bean
		RedisConnectionFactory redisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisHttpSession
	static class MultipleConnectionFactoryRedisConfig {

		@Bean
		RedisConnectionFactory secondaryRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisHttpSession(redisNamespace = "myRedisNamespace")
	static class CustomRedisHttpSessionConfiguration {

	}

	@Configuration
	@EnableRedisHttpSession(redisNamespace = "${session.redis.namespace}")
	static class CustomRedisHttpSessionConfiguration2 {

	}

	@EnableRedisHttpSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		@Order(0)
		SessionRepositoryCustomizer<RedisSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

		@Bean
		@Order(1)
		SessionRepositoryCustomizer<RedisSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository
					.setDefaultMaxInactiveInterval(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
		}

	}

}
