/*
 * Copyright 2014-2016 the original author or authors.
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
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;

/**
 * @author Eddú Meléndez
 */
public class MongoHttpSessionConfigurationTest {

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
	public void defaultCollectionName() {
		registerAndRefresh(DefaultConfiguration.class);
		MongoHttpSessionConfiguration session = this.context
				.getBean(MongoHttpSessionConfiguration.class);
		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "collectionName")).isEqualTo(
				"sessions");
	}

	@Test
	public void customCollectionName() {
		registerAndRefresh(CustomCollectionNameConfiguration.class);
		MongoHttpSessionConfiguration session = this.context
				.getBean(MongoHttpSessionConfiguration.class);
		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "collectionName")).isEqualTo(
				"testSessions");
	}

	@Test
	public void setCustomCollectionName() {
		registerAndRefresh(CustomConfiguration.class, CustomCollectionNameSetConfiguration.class);
		MongoHttpSessionConfiguration session = this.context
				.getBean(MongoHttpSessionConfiguration.class);
		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "collectionName")).isEqualTo(
				"customSession");
	}

	@Test
	public void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomConfiguration.class, CustomMaxInactiveIntervalInSecondsSetConfiguration.class);
		MongoHttpSessionConfiguration session = this.context
				.getBean(MongoHttpSessionConfiguration.class);
		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "maxInactiveIntervalInSeconds")).isEqualTo(
				10);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
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
	@EnableMongoHttpSession(collectionName = "testSessions")
	static class CustomCollectionNameConfiguration extends BaseConfiguration {
	}

	@Configuration
	@EnableMongoHttpSession
	static class DefaultConfiguration extends BaseConfiguration {
	}

	@Configuration
	static class CustomCollectionNameSetConfiguration extends MongoHttpSessionConfiguration {

		CustomCollectionNameSetConfiguration() {
			setCollectionName("customSession");
		}

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration extends MongoHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveIntervalInSeconds(10);
		}

	}

	@Configuration
	static class CustomConfiguration extends BaseConfiguration {

	}

}
