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

package org.springframework.session.jdbc.config.annotation.web.http;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link JdbcHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 * @author Eddú Meléndez
 * @since 1.2.0
 */
public class JdbcHttpSessionConfigurationTests {

	private static final String TABLE_NAME = "TEST_SESSION";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private static final String TABLE_NAME_SYSTEM_PROPERTY = "spring.session.jdbc.tableName";

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@After
	public void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void noDataSourceConfiguration() {
		this.thrown.expect(UnsatisfiedDependencyException.class);
		this.thrown.expectMessage("springSessionJdbcOperations");

		registerAndRefresh(EmptyConfiguration.class);
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(JdbcOperationsSessionRepository.class))
				.isNotNull();
	}

	@Test
	public void customTableName() {
		registerAndRefresh(CustomTableNameConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName"))
				.isEqualTo(TABLE_NAME);
	}

	@Test
	public void customTableNameSystemProperty() {
		System.setProperty(TABLE_NAME_SYSTEM_PROPERTY, TABLE_NAME);

		try {
			registerAndRefresh(DefaultConfiguration.class);

			JdbcOperationsSessionRepository repository = this.context
					.getBean(JdbcOperationsSessionRepository.class);
			assertThat(repository).isNotNull();
			assertThat(ReflectionTestUtils.getField(repository, "tableName"))
					.isEqualTo(TABLE_NAME);
		}
		finally {
			System.clearProperty(TABLE_NAME_SYSTEM_PROPERTY);
		}
	}

	@Test
	public void setCustomTableName() {
		registerAndRefresh(BaseConfiguration.class,
				CustomTableNameSetConfiguration.class);

		JdbcHttpSessionConfiguration repository = this.context
				.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName")).isEqualTo(
				"custom_session");
	}

	@Test
	public void setCustomMaxInactiveIntervalInSeconds() {
		registerAndRefresh(BaseConfiguration.class,
				CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		JdbcHttpSessionConfiguration repository = this.context
				.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "maxInactiveIntervalInSeconds")).isEqualTo(
				10);
	}

	@Test
	public void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customLobHandlerConfiguration() {
		registerAndRefresh(CustomLobHandlerConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		LobHandler lobHandler = this.context.getBean(LobHandler.class);
		assertThat(repository).isNotNull();
		assertThat(lobHandler).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "lobHandler"))
				.isEqualTo(lobHandler);
	}

	@Test
	public void customConversionServiceConfiguration() {
		registerAndRefresh(CustomConversionServiceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		ConversionService conversionService = this.context
				.getBean("springSessionConversionService", ConversionService.class);
		assertThat(repository).isNotNull();
		assertThat(conversionService).isNotNull();
		Object repositoryConversionService = ReflectionTestUtils.getField(repository,
				"conversionService");
		assertThat(repositoryConversionService).isEqualTo(conversionService);
	}

	@Test
	public void resolveTableNameByPropertyPlaceholder() {
		this.context.setEnvironment(new MockEnvironment().withProperty("session.jdbc.tableName", "custom_session_table"));
		registerAndRefresh(CustomJdbcHttpSessionConfiguration.class);
		JdbcHttpSessionConfiguration configuration = this.context.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "tableName")).isEqualTo("custom_session_table");
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	@EnableJdbcHttpSession
	static class EmptyConfiguration {
	}

	static class BaseConfiguration {

		@Bean
		public DataSource dataSource() {
			return mock(DataSource.class);
		}

		@Bean
		public PlatformTransactionManager transactionManager() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@Configuration
	@EnableJdbcHttpSession
	static class DefaultConfiguration extends BaseConfiguration {
	}

	@Configuration
	@EnableJdbcHttpSession(tableName = TABLE_NAME)
	static class CustomTableNameConfiguration extends BaseConfiguration {
	}

	@Configuration
	static class CustomTableNameSetConfiguration extends JdbcHttpSessionConfiguration {

		CustomTableNameSetConfiguration() {
			setTableName("custom_session");
		}

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration extends JdbcHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveIntervalInSeconds(10);
		}

	}

	@Configuration
	@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration
			extends BaseConfiguration {
	}

	@Configuration
	@EnableJdbcHttpSession
	static class CustomLobHandlerConfiguration extends BaseConfiguration {

		@Bean
		public LobHandler springSessionLobHandler() {
			return mock(LobHandler.class);
		}

	}

	@Configuration
	@EnableJdbcHttpSession
	static class CustomConversionServiceConfiguration extends BaseConfiguration {

		@Bean
		public ConversionService springSessionConversionService() {
			return mock(ConversionService.class);
		}

	}

	@Configuration
	@EnableJdbcHttpSession(tableName = "${session.jdbc.tableName}")
	static class CustomJdbcHttpSessionConfiguration extends BaseConfiguration {

		@Bean
		public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}

}
