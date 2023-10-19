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

package org.springframework.session.data.mongo.config.annotation.web.http;

import java.net.UnknownHostException;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.IndexResolver;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JacksonMongoSessionConverter;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

/**
 * Tests for {@link MongoHttpSessionConfiguration}.
 *
 * @author Eddú Meléndez
 * @author Vedran Pavic
 */
public class MongoHttpSessionConfigurationTests {

	private static final String COLLECTION_NAME = "testSessions";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void after() {

		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void noMongoOperationsConfiguration() {

		assertThatExceptionOfType(UnsatisfiedDependencyException.class)
			.isThrownBy(() -> registerAndRefresh(EmptyConfiguration.class))
			.withMessageContaining("mongoSessionRepository");
	}

	@Test
	void defaultConfiguration() {

		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(MongoIndexedSessionRepository.class)).isNotNull();
	}

	@Test
	void customCollectionName() {

		registerAndRefresh(CustomCollectionNameConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "collectionName")).isEqualTo(COLLECTION_NAME);
	}

	@Test
	void setCustomCollectionName() {

		registerAndRefresh(CustomCollectionNameSetConfiguration.class);

		MongoHttpSessionConfiguration session = this.context.getBean(MongoHttpSessionConfiguration.class);

		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "collectionName")).isEqualTo(COLLECTION_NAME);
	}

	@Test
	void customMaxInactiveIntervalInSeconds() {

		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(repository).extracting("defaultMaxInactiveInterval")
			.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	void setCustomMaxInactiveIntervalInSeconds() {

		registerAndRefresh(CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(repository).extracting("defaultMaxInactiveInterval")
			.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	void setCustomSessionConverterConfiguration() {

		registerAndRefresh(CustomSessionConverterConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);
		AbstractMongoSessionConverter mongoSessionConverter = this.context.getBean(AbstractMongoSessionConverter.class);

		assertThat(repository).isNotNull();
		assertThat(mongoSessionConverter).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "mongoSessionConverter")).isEqualTo(mongoSessionConverter);
	}

	@Test
	void resolveCollectionNameByPropertyPlaceholder() {

		this.context
			.setEnvironment(new MockEnvironment().withProperty("session.mongo.collectionName", COLLECTION_NAME));
		registerAndRefresh(CustomMongoJdbcSessionConfiguration.class);

		MongoHttpSessionConfiguration configuration = this.context.getBean(MongoHttpSessionConfiguration.class);

		assertThat(ReflectionTestUtils.getField(configuration, "collectionName")).isEqualTo(COLLECTION_NAME);
	}

	@Test
	void sessionRepositoryCustomizer() {

		registerAndRefresh(MongoConfiguration.class, SessionRepositoryCustomizerConfiguration.class);

		MongoIndexedSessionRepository sessionRepository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ofSeconds(10000));
	}

	@Test
	void customIndexResolverConfigurationWithDefaultMongoSessionConverter() {

		registerAndRefresh(MongoConfiguration.class,
				CustomIndexResolverConfigurationWithDefaultMongoSessionConverter.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);
		IndexResolver<Session> indexResolver = this.context.getBean(IndexResolver.class);

		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).extracting("mongoSessionConverter")
			.hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	@Test
	void customIndexResolverConfigurationWithProvidedMongoSessionConverter() {

		registerAndRefresh(MongoConfiguration.class,
				CustomIndexResolverConfigurationWithProvidedMongoSessionConverter.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);
		IndexResolver<Session> indexResolver = this.context.getBean(IndexResolver.class);

		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).extracting("mongoSessionConverter")
			.hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	@Test
	void importConfigAndCustomize() {
		registerAndRefresh(ImportConfigAndCustomizeConfiguration.class);
		MongoIndexedSessionRepository sessionRepository = this.context.getBean(MongoIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ZERO);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {

		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	@EnableMongoHttpSession
	static class EmptyConfiguration {

	}

	static class BaseConfiguration {

		@Bean
		MongoOperations mongoOperations() throws UnknownHostException {

			MongoOperations mongoOperations = mock(MongoOperations.class);
			IndexOperations indexOperations = mock(IndexOperations.class);

			given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

			return mongoOperations;
		}

	}

	@Configuration
	@EnableMongoHttpSession
	static class DefaultConfiguration extends BaseConfiguration {

	}

	@Configuration
	static class MongoConfiguration extends BaseConfiguration {

	}

	@Configuration
	@EnableMongoHttpSession(collectionName = COLLECTION_NAME)
	static class CustomCollectionNameConfiguration extends BaseConfiguration {

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomCollectionNameSetConfiguration extends MongoHttpSessionConfiguration {

		CustomCollectionNameSetConfiguration() {
			setCollectionName(COLLECTION_NAME);
		}

	}

	@Configuration
	@EnableMongoHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration extends BaseConfiguration {

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration extends MongoHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveInterval(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
		}

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomSessionConverterConfiguration extends MongoHttpSessionConfiguration {

		@Bean
		AbstractMongoSessionConverter mongoSessionConverter() {
			return mock(AbstractMongoSessionConverter.class);
		}

	}

	@Configuration
	@EnableMongoHttpSession(collectionName = "${session.mongo.collectionName}")
	static class CustomMongoJdbcSessionConfiguration extends BaseConfiguration {

		@Bean
		PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoHttpSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		@Order(0)
		SessionRepositoryCustomizer<MongoIndexedSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

		@Bean
		@Order(1)
		SessionRepositoryCustomizer<MongoIndexedSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ofSeconds(10000));
		}

	}

	@Configuration
	@EnableMongoHttpSession
	static class CustomIndexResolverConfigurationWithDefaultMongoSessionConverter {

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@Configuration
	@EnableMongoHttpSession
	static class CustomIndexResolverConfigurationWithProvidedMongoSessionConverter {

		@Bean
		AbstractMongoSessionConverter mongoSessionConverter() {
			return new JacksonMongoSessionConverter();
		}

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(MongoHttpSessionConfiguration.class)
	static class ImportConfigAndCustomizeConfiguration extends BaseConfiguration {

		@Bean
		SessionRepositoryCustomizer<MongoIndexedSessionRepository> sessionRepositoryCustomizer() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

	}

}
