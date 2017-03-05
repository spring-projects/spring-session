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
package org.springframework.session.data.mongo.config.annotation.web.http;

import java.net.UnknownHostException;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.MongoOperationsSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MongoHttpSessionConfiguration}.
 *
 * @author Eddú Meléndez
 * @author Vedran Pavic
 */
public class MongoHttpSessionConfigurationTests {

	private static final String COLLECTION_NAME = "testSessions";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@After
	public void after() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void noMongoOperationsConfiguration() {
		this.thrown.expect(UnsatisfiedDependencyException.class);
		this.thrown.expectMessage("mongoSessionRepository");

		registerAndRefresh(EmptyConfiguration.class);
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(MongoOperationsSessionRepository.class))
				.isNotNull();
	}

	@Test
	public void customCollectionName() {
		registerAndRefresh(CustomCollectionNameConfiguration.class);

		MongoOperationsSessionRepository repository = this.context
				.getBean(MongoOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "collectionName"))
				.isEqualTo(COLLECTION_NAME);
	}

	@Test
	public void setCustomCollectionName() {
		registerAndRefresh(CustomCollectionNameSetConfiguration.class);

		MongoHttpSessionConfiguration session = this.context
				.getBean(MongoHttpSessionConfiguration.class);
		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "collectionName"))
				.isEqualTo(COLLECTION_NAME);
	}

	@Test
	public void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		MongoOperationsSessionRepository repository = this.context
				.getBean(MongoOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "maxInactiveIntervalInSeconds"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void setCustomMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		MongoOperationsSessionRepository repository = this.context
				.getBean(MongoOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "maxInactiveIntervalInSeconds"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void setCustomSessionConverterConfiguration() {
		registerAndRefresh(CustomSessionConverterConfiguration.class);

		MongoOperationsSessionRepository repository = this.context
				.getBean(MongoOperationsSessionRepository.class);
		AbstractMongoSessionConverter mongoSessionConverter = this.context
				.getBean(AbstractMongoSessionConverter.class);
		assertThat(repository).isNotNull();
		assertThat(mongoSessionConverter).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "mongoSessionConverter"))
				.isEqualTo(mongoSessionConverter);
	}

	@Test
	public void resolveCollectionNameByPropertyPlaceholder() {
		this.context.setEnvironment(new MockEnvironment().withProperty("session.mongo.collectionName", COLLECTION_NAME));
		registerAndRefresh(CustomMongoJdbcSessionConfiguration.class);
		MongoHttpSessionConfiguration configuration = this.context.getBean(MongoHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "collectionName")).isEqualTo(COLLECTION_NAME);
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
		public MongoOperations mongoOperations() throws UnknownHostException {
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
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomSessionConverterConfiguration extends MongoHttpSessionConfiguration {

		@Bean
		public AbstractMongoSessionConverter mongoSessionConverter() {
			return mock(AbstractMongoSessionConverter.class);
		}

	}

	@Configuration
	@EnableMongoHttpSession(collectionName = "${session.mongo.collectionName}")
	static class CustomMongoJdbcSessionConfiguration extends BaseConfiguration {

		@Bean
		public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}

}
