/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.session.jdbc.config.annotation.web.http;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link JdbcHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 * @author Eddú Meléndez
 * @since 1.2.0
 */
class JdbcHttpSessionConfigurationTests {

	private static final String TABLE_NAME = "TEST_SESSION";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private static final String CLEANUP_CRON_EXPRESSION = "0 0 * * * *";

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void noDataSourceConfiguration() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(NoDataSourceConfiguration.class))
				.withMessageContaining("expected at least 1 bean which qualifies as autowire candidate");
	}

	@Test
	void defaultConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, DefaultConfiguration.class);

		JdbcOperationsSessionRepository sessionRepository = this.context.getBean(JdbcOperationsSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository).extracting("transactionOperations")
				.hasFieldOrPropertyWithValue("propagationBehavior", TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Test
	void customTableNameAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class, CustomTableNameAnnotationConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName")).isEqualTo(TABLE_NAME);
	}

	@Test
	void customTableNameSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomTableNameSetterConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName")).isEqualTo(TABLE_NAME);
	}

	@Test
	void customMaxInactiveIntervalInSecondsAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomMaxInactiveIntervalInSecondsAnnotationConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	void customMaxInactiveIntervalInSecondsSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomMaxInactiveIntervalInSecondsSetterConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	void customCleanupCronAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class, CustomCleanupCronExpressionAnnotationConfiguration.class);

		JdbcHttpSessionConfiguration configuration = this.context.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(configuration).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "cleanupCron")).isEqualTo(CLEANUP_CRON_EXPRESSION);
	}

	@Test
	void customCleanupCronSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomCleanupCronExpressionSetterConfiguration.class);

		JdbcHttpSessionConfiguration configuration = this.context.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(configuration).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "cleanupCron")).isEqualTo(CLEANUP_CRON_EXPRESSION);
	}

	@Test
	void customFlushModeAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class, CustomFlushModeExpressionAnnotationConfiguration.class);
		assertThat(this.context.getBean(JdbcHttpSessionConfiguration.class)).hasFieldOrPropertyWithValue("flushMode",
				FlushMode.IMMEDIATE);
	}

	@Test
	void customFlushModeSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomFlushModeExpressionSetterConfiguration.class);
		assertThat(this.context.getBean(JdbcHttpSessionConfiguration.class)).hasFieldOrPropertyWithValue("flushMode",
				FlushMode.IMMEDIATE);
	}

	@Test
	void customSaveModeAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class, CustomSaveModeExpressionAnnotationConfiguration.class);
		assertThat(this.context.getBean(JdbcHttpSessionConfiguration.class)).hasFieldOrPropertyWithValue("saveMode",
				SaveMode.ALWAYS);
	}

	@Test
	void customSaveModeSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomSaveModeExpressionSetterConfiguration.class);
		assertThat(this.context.getBean(JdbcHttpSessionConfiguration.class)).hasFieldOrPropertyWithValue("saveMode",
				SaveMode.ALWAYS);
	}

	@Test
	void qualifiedDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, QualifiedDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("qualifiedDataSource", DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource")).isEqualTo(dataSource);
	}

	@Test
	void primaryDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, PrimaryDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("primaryDataSource", DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource")).isEqualTo(dataSource);
	}

	@Test
	void qualifiedAndPrimaryDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, QualifiedAndPrimaryDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("qualifiedDataSource", DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource")).isEqualTo(dataSource);
	}

	@Test
	void namedDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, NamedDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("dataSource", DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource")).isEqualTo(dataSource);
	}

	@Test
	void multipleDataSourceConfiguration() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(
						() -> registerAndRefresh(DataSourceConfiguration.class, MultipleDataSourceConfiguration.class))
				.withMessageContaining("expected single matching bean but found 2");
	}

	@Test
	void customTransactionOperationsConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomTransactionOperationsConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		TransactionOperations transactionOperations = this.context.getBean(TransactionOperations.class);
		assertThat(repository).isNotNull();
		assertThat(transactionOperations).isNotNull();
		assertThat(repository).hasFieldOrPropertyWithValue("transactionOperations", transactionOperations);
	}

	@Test
	void customLobHandlerConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomLobHandlerConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		LobHandler lobHandler = this.context.getBean(LobHandler.class);
		assertThat(repository).isNotNull();
		assertThat(lobHandler).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "lobHandler")).isEqualTo(lobHandler);
	}

	@Test
	void customConversionServiceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomConversionServiceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context.getBean(JdbcOperationsSessionRepository.class);
		ConversionService conversionService = this.context.getBean("springSessionConversionService",
				ConversionService.class);
		assertThat(repository).isNotNull();
		assertThat(conversionService).isNotNull();
		Object repositoryConversionService = ReflectionTestUtils.getField(repository, "conversionService");
		assertThat(repositoryConversionService).isEqualTo(conversionService);
	}

	@Test
	void resolveTableNameByPropertyPlaceholder() {
		this.context
				.setEnvironment(new MockEnvironment().withProperty("session.jdbc.tableName", "custom_session_table"));
		registerAndRefresh(DataSourceConfiguration.class, CustomJdbcHttpSessionConfiguration.class);
		JdbcHttpSessionConfiguration configuration = this.context.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "tableName")).isEqualTo("custom_session_table");
	}

	@Test
	void sessionRepositoryCustomizer() {
		registerAndRefresh(DataSourceConfiguration.class, SessionRepositoryCustomizerConfiguration.class);
		JdbcOperationsSessionRepository sessionRepository = this.context.getBean(JdbcOperationsSessionRepository.class);
		assertThat(sessionRepository).hasFieldOrPropertyWithValue("defaultMaxInactiveInterval",
				MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@EnableJdbcHttpSession
	static class NoDataSourceConfiguration {

	}

	@Configuration
	static class DataSourceConfiguration {

		@Bean
		public DataSource defaultDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		public PlatformTransactionManager transactionManager() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@EnableJdbcHttpSession
	static class DefaultConfiguration {

	}

	@EnableJdbcHttpSession(tableName = TABLE_NAME)
	static class CustomTableNameAnnotationConfiguration {

	}

	@Configuration
	static class CustomTableNameSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomTableNameSetterConfiguration() {
			setTableName(TABLE_NAME);
		}

	}

	@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsAnnotationConfiguration {

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetterConfiguration() {
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@EnableJdbcHttpSession(cleanupCron = CLEANUP_CRON_EXPRESSION)
	static class CustomCleanupCronExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomCleanupCronExpressionSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomCleanupCronExpressionSetterConfiguration() {
			setCleanupCron(CLEANUP_CRON_EXPRESSION);
		}

	}

	@EnableJdbcHttpSession(flushMode = FlushMode.IMMEDIATE)
	static class CustomFlushModeExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomFlushModeExpressionSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomFlushModeExpressionSetterConfiguration() {
			setFlushMode(FlushMode.IMMEDIATE);
		}

	}

	@EnableJdbcHttpSession(saveMode = SaveMode.ALWAYS)
	static class CustomSaveModeExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomSaveModeExpressionSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomSaveModeExpressionSetterConfiguration() {
			setSaveMode(SaveMode.ALWAYS);
		}

	}

	@EnableJdbcHttpSession
	static class QualifiedDataSourceConfiguration {

		@Bean
		@SpringSessionDataSource
		public DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

	}

	@EnableJdbcHttpSession
	static class PrimaryDataSourceConfiguration {

		@Bean
		@Primary
		public DataSource primaryDataSource() {
			return mock(DataSource.class);
		}

	}

	@EnableJdbcHttpSession
	static class QualifiedAndPrimaryDataSourceConfiguration {

		@Bean
		@SpringSessionDataSource
		public DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		@Primary
		public DataSource primaryDataSource() {
			return mock(DataSource.class);
		}

	}

	@EnableJdbcHttpSession
	static class NamedDataSourceConfiguration {

		@Bean
		public DataSource dataSource() {
			return mock(DataSource.class);
		}

	}

	@EnableJdbcHttpSession
	static class MultipleDataSourceConfiguration {

		@Bean
		public DataSource secondaryDataSource() {
			return mock(DataSource.class);
		}

	}

	@EnableJdbcHttpSession
	static class CustomTransactionOperationsConfiguration {

		@Bean
		public TransactionOperations springSessionTransactionOperations() {
			return TransactionOperations.withoutTransaction();
		}

	}

	@EnableJdbcHttpSession
	static class CustomLobHandlerConfiguration {

		@Bean
		public LobHandler springSessionLobHandler() {
			return mock(LobHandler.class);
		}

	}

	@EnableJdbcHttpSession
	static class CustomConversionServiceConfiguration {

		@Bean
		public ConversionService springSessionConversionService() {
			return mock(ConversionService.class);
		}

	}

	@EnableJdbcHttpSession(tableName = "${session.jdbc.tableName}")
	static class CustomJdbcHttpSessionConfiguration {

		@Bean
		public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}

	@EnableJdbcHttpSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		@Order(0)
		public SessionRepositoryCustomizer<JdbcOperationsSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(0);
		}

		@Bean
		@Order(1)
		public SessionRepositoryCustomizer<JdbcOperationsSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository
					.setDefaultMaxInactiveInterval(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

}
