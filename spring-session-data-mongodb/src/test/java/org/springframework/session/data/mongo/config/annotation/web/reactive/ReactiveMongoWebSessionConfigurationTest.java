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

package org.springframework.session.data.mongo.config.annotation.web.reactive;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.session.IndexResolver;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JacksonMongoSessionConverter;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.session.data.mongo.MongoSession;
import org.springframework.session.data.mongo.ReactiveMongoSessionRepository;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.session.WebSessionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

/**
 * Verify various configurations through {@link EnableSpringWebSession}.
 *
 * @author Greg Turnquist
 * @author Vedran Pavic
 */
public class ReactiveMongoWebSessionConfigurationTest {

	private AnnotationConfigApplicationContext context;

	@AfterEach
	void tearDown() {

		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void enableSpringWebSessionConfiguresThings() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(GoodConfig.class);
		this.context.refresh();

		WebSessionManager webSessionManagerFoundByType = this.context.getBean(WebSessionManager.class);
		Object webSessionManagerFoundByName = this.context.getBean(WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME);

		assertThat(webSessionManagerFoundByType).isNotNull();
		assertThat(webSessionManagerFoundByName).isNotNull();
		assertThat(webSessionManagerFoundByType).isEqualTo(webSessionManagerFoundByName);

		assertThat(this.context.getBean(ReactiveSessionRepository.class)).isNotNull();
	}

	@Test
	void missingReactorSessionRepositoryBreaksAppContext() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(BadConfig.class);

		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(this.context::refresh)
				.withMessageContaining("Error creating bean with name 'reactiveMongoSessionRepository'")
				.withMessageContaining(
						"No qualifying bean of type '" + ReactiveMongoOperations.class.getCanonicalName());
	}

	@Test
	void defaultSessionConverterShouldBeJdkWhenOnClasspath() throws IllegalAccessException {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(GoodConfig.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);

		AbstractMongoSessionConverter converter = findMongoSessionConverter(repository);

		assertThat(converter).isOfAnyClassIn(JdkMongoSessionConverter.class);
	}

	@Test
	void overridingMongoSessionConverterWithBeanShouldWork() throws IllegalAccessException {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(OverrideSessionConverterConfig.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);

		AbstractMongoSessionConverter converter = findMongoSessionConverter(repository);

		assertThat(converter).isOfAnyClassIn(JacksonMongoSessionConverter.class);
	}

	@Test
	void overridingIntervalAndCollectionNameThroughAnnotationShouldWork() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(OverrideMongoParametersConfig.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);

		assertThat(repository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ofSeconds(123));
		assertThat(repository).extracting("collectionName").isEqualTo("test-case");
	}

	@Test
	void reactiveAndBlockingMongoOperationsShouldEnsureIndexing() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(ConfigWithReactiveAndImperativeMongoOperations.class);
		this.context.refresh();

		MongoOperations operations = this.context.getBean(MongoOperations.class);
		IndexOperations indexOperations = this.context.getBean(IndexOperations.class);

		verify(operations, times(1)).indexOps((String) any());
		verify(indexOperations, times(1)).getIndexInfo();
		verify(indexOperations, times(1)).ensureIndex(any());
	}

	@Test
	void overrideCollectionAndInactiveIntervalThroughConfigurationOptions() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(CustomizedReactiveConfiguration.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);

		assertThat(repository.getCollectionName()).isEqualTo("custom-collection");
		assertThat(repository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ofSeconds(123));
	}

	@Test
	void sessionRepositoryCustomizer() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(SessionRepositoryCustomizerConfiguration.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);

		assertThat(repository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ofSeconds(10000));
	}

	@Test
	void customIndexResolverConfigurationWithDefaultMongoSessionConverter() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(CustomIndexResolverConfigurationWithDefaultMongoSessionConverter.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);
		IndexResolver<MongoSession> indexResolver = this.context.getBean(IndexResolver.class);

		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).extracting("mongoSessionConverter").hasFieldOrPropertyWithValue("indexResolver",
				indexResolver);
	}

	@Test
	void customIndexResolverConfigurationWithProvidedMongoSessionConverter() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(CustomIndexResolverConfigurationWithProvidedtMongoSessionConverter.class);
		this.context.refresh();

		ReactiveMongoSessionRepository repository = this.context.getBean(ReactiveMongoSessionRepository.class);
		IndexResolver<MongoSession> indexResolver = this.context.getBean(IndexResolver.class);

		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).extracting("mongoSessionConverter").hasFieldOrPropertyWithValue("indexResolver",
				indexResolver);
	}

	@Test
	void importConfigAndCustomize() {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(ImportConfigAndCustomizeConfiguration.class);
		this.context.refresh();
		ReactiveMongoSessionRepository sessionRepository = this.context.getBean(ReactiveMongoSessionRepository.class);
		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ZERO);
	}

	/**
	 * Reflectively extract the {@link AbstractMongoSessionConverter} from the
	 * {@link ReactiveMongoSessionRepository}. This is to avoid expanding the surface area
	 * of the API.
	 */
	private AbstractMongoSessionConverter findMongoSessionConverter(ReactiveMongoSessionRepository repository) {

		Field field = ReflectionUtils.findField(ReactiveMongoSessionRepository.class, "mongoSessionConverter");
		ReflectionUtils.makeAccessible(field);
		try {
			return (AbstractMongoSessionConverter) field.get(repository);
		}
		catch (IllegalAccessException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * A configuration with all the right parts.
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class GoodConfig {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

	}

	/**
	 * A configuration where no {@link ReactiveMongoOperations} is defined. It's BAD!
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class BadConfig {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class OverrideSessionConverterConfig {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		AbstractMongoSessionConverter mongoSessionConverter() {
			return new JacksonMongoSessionConverter();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession(maxInactiveIntervalInSeconds = 123, collectionName = "test-case")
	static class OverrideMongoParametersConfig {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class ConfigWithReactiveAndImperativeMongoOperations {

		@Bean
		ReactiveMongoOperations reactiveMongoOperations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		IndexOperations indexOperations() {

			IndexOperations indexOperations = mock(IndexOperations.class);
			given(indexOperations.getIndexInfo()).willReturn(Collections.emptyList());
			return indexOperations;
		}

		@Bean
		MongoOperations mongoOperations(IndexOperations indexOperations) {

			MongoOperations mongoOperations = mock(MongoOperations.class);
			given(mongoOperations.indexOps((String) any())).willReturn(indexOperations);
			return mongoOperations;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableSpringWebSession
	static class CustomizedReactiveConfiguration extends ReactiveMongoWebSessionConfiguration {

		CustomizedReactiveConfiguration() {

			this.setCollectionName("custom-collection");
			this.setMaxInactiveInterval(Duration.ofSeconds(123));
		}

		@Bean
		ReactiveMongoOperations reactiveMongoOperations() {
			return mock(ReactiveMongoOperations.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		@Order(0)
		ReactiveSessionRepositoryCustomizer<ReactiveMongoSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

		@Bean
		@Order(1)
		ReactiveSessionRepositoryCustomizer<ReactiveMongoSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ofSeconds(10000));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class CustomIndexResolverConfigurationWithDefaultMongoSessionConverter {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<MongoSession> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableMongoWebSession
	static class CustomIndexResolverConfigurationWithProvidedtMongoSessionConverter {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		JacksonMongoSessionConverter jacksonMongoSessionConverter() {
			return new JacksonMongoSessionConverter();
		}

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<MongoSession> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(ReactiveMongoWebSessionConfiguration.class)
	static class ImportConfigAndCustomizeConfiguration {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		ReactiveSessionRepositoryCustomizer<ReactiveMongoSessionRepository> sessionRepositoryCustomizer() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

	}

}
