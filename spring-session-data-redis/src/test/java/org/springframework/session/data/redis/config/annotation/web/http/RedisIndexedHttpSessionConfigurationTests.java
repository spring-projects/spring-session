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
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerationStrategy;
import org.springframework.session.UuidSessionIdGenerationStrategy;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RedisIndexedHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 */
class RedisIndexedHttpSessionConfigurationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private static final String CLEANUP_CRON_EXPRESSION = "0 0 * * * *";

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
		RedisIndexedHttpSessionConfiguration configuration = this.context
				.getBean(RedisIndexedHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "redisNamespace")).isEqualTo("myRedisNamespace");
	}

	@Test
	void resolveValueByPlaceholder() {
		this.context
				.setEnvironment(new MockEnvironment().withProperty("session.redis.namespace", "customRedisNamespace"));
		registerAndRefresh(RedisConfig.class, PropertySourceConfiguration.class,
				CustomRedisHttpSessionConfiguration2.class);
		RedisIndexedHttpSessionConfiguration configuration = this.context
				.getBean(RedisIndexedHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "redisNamespace")).isEqualTo("customRedisNamespace");
	}

	@Test
	void customFlushImmediately() {
		registerAndRefresh(RedisConfig.class, CustomFlushImmediatelyConfiguration.class);
		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
		assertThat(ReflectionTestUtils.getField(sessionRepository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void setCustomFlushImmediately() {
		registerAndRefresh(RedisConfig.class, CustomFlushImmediatelySetConfiguration.class);
		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
		assertThat(ReflectionTestUtils.getField(sessionRepository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void customCleanupCronAnnotation() {
		registerAndRefresh(RedisConfig.class, CustomCleanupCronExpressionAnnotationConfiguration.class);

		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("cleanupCron").isEqualTo(CLEANUP_CRON_EXPRESSION);
	}

	@Test
	void customSaveModeAnnotation() {
		registerAndRefresh(RedisConfig.class, CustomSaveModeExpressionAnnotationConfiguration.class);
		assertThat(this.context.getBean(RedisIndexedSessionRepository.class)).hasFieldOrPropertyWithValue("saveMode",
				SaveMode.ALWAYS);
	}

	@Test
	void qualifiedConnectionFactoryRedisConfig() {
		registerAndRefresh(RedisConfig.class, QualifiedConnectionFactoryRedisConfig.class);

		RedisIndexedSessionRepository repository = this.context.getBean(RedisIndexedSessionRepository.class);
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

		RedisIndexedSessionRepository repository = this.context.getBean(RedisIndexedSessionRepository.class);
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

		RedisIndexedSessionRepository repository = this.context.getBean(RedisIndexedSessionRepository.class);
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

		RedisIndexedSessionRepository repository = this.context.getBean(RedisIndexedSessionRepository.class);
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
	void customIndexResolverConfiguration() {
		registerAndRefresh(RedisConfig.class, CustomIndexResolverConfiguration.class);
		RedisIndexedSessionRepository repository = this.context.getBean(RedisIndexedSessionRepository.class);
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver = this.context.getBean(IndexResolver.class);
		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	@Test // gh-1252
	void customRedisMessageListenerContainerConfig() {
		registerAndRefresh(RedisConfig.class, CustomRedisMessageListenerContainerConfig.class);
		Map<String, RedisMessageListenerContainer> beans = this.context
				.getBeansOfType(RedisMessageListenerContainer.class);
		assertThat(beans).hasSize(2);
		assertThat(beans).containsKeys("springSessionRedisMessageListenerContainer", "redisMessageListenerContainer");
	}

	@Test
	void sessionRepositoryCustomizer() {
		registerAndRefresh(RedisConfig.class, SessionRepositoryCustomizerConfiguration.class);
		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval")
				.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	void importConfigAndCustomize() {
		registerAndRefresh(RedisConfig.class, ImportConfigAndCustomizeConfiguration.class);
		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ZERO);
	}

	@Test
	void registerWhenSessionIdGenerationStrategyBeanThenUses() {
		registerAndRefresh(RedisConfig.class, SessionIdGenerationStrategyConfiguration.class);
		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("sessionIdGenerationStrategy")
				.isInstanceOf(TestSessionIdGenerationStrategy.class);
	}

	@Test
	void registerWhenNoSessionIdGenerationStrategyBeanThenDefault() {
		registerAndRefresh(RedisConfig.class, DefaultConfiguration.class);
		RedisIndexedSessionRepository sessionRepository = this.context.getBean(RedisIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("sessionIdGenerationStrategy")
				.isInstanceOf(UuidSessionIdGenerationStrategy.class);
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
	static class CustomFlushImmediatelySetConfiguration extends RedisIndexedHttpSessionConfiguration {

		CustomFlushImmediatelySetConfiguration() {
			setFlushMode(FlushMode.IMMEDIATE);
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession(flushMode = FlushMode.IMMEDIATE)
	static class CustomFlushImmediatelyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedHttpSession(cleanupCron = CLEANUP_CRON_EXPRESSION)
	static class CustomCleanupCronExpressionAnnotationConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedHttpSession(saveMode = SaveMode.ALWAYS)
	static class CustomSaveModeExpressionAnnotationConfiguration {

	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class QualifiedConnectionFactoryRedisConfig {

		@Bean
		@SpringSessionRedisConnectionFactory
		RedisConnectionFactory qualifiedRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class PrimaryConnectionFactoryRedisConfig {

		@Bean
		@Primary
		RedisConnectionFactory primaryRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession
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
	@EnableRedisIndexedHttpSession
	static class NamedConnectionFactoryRedisConfig {

		@Bean
		RedisConnectionFactory redisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class MultipleConnectionFactoryRedisConfig {

		@Bean
		RedisConnectionFactory secondaryRedisConnectionFactory() {
			return mockRedisConnectionFactory();
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession(redisNamespace = "myRedisNamespace")
	static class CustomRedisHttpSessionConfiguration {

	}

	@Configuration
	@EnableRedisIndexedHttpSession(redisNamespace = "${session.redis.namespace}")
	static class CustomRedisHttpSessionConfiguration2 {

	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class CustomIndexResolverConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession
	static class CustomRedisMessageListenerContainerConfig {

		@Bean
		RedisMessageListenerContainer redisMessageListenerContainer() {
			return mock(RedisMessageListenerContainer.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedHttpSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		@Order(0)
		SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

		@Bean
		@Order(1)
		SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository
					.setDefaultMaxInactiveInterval(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(RedisIndexedHttpSessionConfiguration.class)
	static class ImportConfigAndCustomizeConfiguration {

		@Bean
		SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionRepositoryCustomizer() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedHttpSession
	static class SessionIdGenerationStrategyConfiguration {

		@Bean
		SessionIdGenerationStrategy sessionIdGenerationStrategy() {
			return new TestSessionIdGenerationStrategy();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedHttpSession
	static class DefaultConfiguration {

	}

	static class TestSessionIdGenerationStrategy implements SessionIdGenerationStrategy {

		@Override
		public String generate() {
			return "test";
		}

	}

}
