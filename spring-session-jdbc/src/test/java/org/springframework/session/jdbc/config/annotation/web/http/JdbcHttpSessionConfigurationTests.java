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

package org.springframework.session.jdbc.config.annotation.web.http;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSourceReadOnly;
import org.springframework.session.jdbc.config.annotation.SpringSessionPlatformTransactionManager;
import org.springframework.session.jdbc.config.annotation.SpringSessionPlatformTransactionManagerReadOnly;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

	private static final String CLEANUP_CRON_EXPRESSION = "0 0 * * * *";

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
		this.thrown.expect(BeanCreationException.class);
		this.thrown.expectMessage(
				"expected at least 1 bean which qualifies as autowire candidate");

		registerAndRefresh(NoDataSourceConfiguration.class);
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, DefaultConfiguration.class);

		assertThat(this.context.getBean(JdbcOperationsSessionRepository.class))
				.isNotNull();
	}

	@Test
	public void customTableNameAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomTableNameAnnotationConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName"))
				.isEqualTo(TABLE_NAME);
	}

	@Test
	public void customTableNameSetter() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomTableNameSetterConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName"))
				.isEqualTo(TABLE_NAME);
	}

	@Test
	public void customMaxInactiveIntervalInSecondsAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomMaxInactiveIntervalInSecondsAnnotationConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customMaxInactiveIntervalInSecondsSetter() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomMaxInactiveIntervalInSecondsSetterConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customCleanupCronAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomCleanupCronExpressionAnnotationConfiguration.class);

		JdbcHttpSessionConfiguration configuration = this.context
				.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(configuration).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "cleanupCron"))
				.isEqualTo(CLEANUP_CRON_EXPRESSION);
	}

	@Test
	public void customCleanupCronSetter() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomCleanupCronExpressionSetterConfiguration.class);

		JdbcHttpSessionConfiguration configuration = this.context
				.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(configuration).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "cleanupCron"))
				.isEqualTo(CLEANUP_CRON_EXPRESSION);
	}

	@Test
	public void qualifiedPlatformTransactionManagerConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				QualifiedTransactionManagerConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		PlatformTransactionManager platformTransactionManager = this.context
				.getBean("qualifiedPlatformTransactionManager",
				PlatformTransactionManager.class);
		assertThat(repository).isNotNull();
		assertThat(platformTransactionManager).isNotNull();
		TransactionTemplate transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperations");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManager);
	}

	@Test
	public void primaryPlatformTransactionManagerConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				PrimaryTransactionManagerConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		PlatformTransactionManager platformTransactionManager = this.context
				.getBean("primaryPlatformTransactionManager",
						PlatformTransactionManager.class);
		assertThat(repository).isNotNull();
		assertThat(platformTransactionManager).isNotNull();
		TransactionTemplate transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperations");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManager);
	}

	@Test
	public void qualifiedAndPrimaryPlatformTransactionManagerConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				QualifiedAndPrimaryTransactionManagerConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		PlatformTransactionManager platformTransactionManager = this.context
				.getBean("qualifiedPlatformTransactionManager",
						PlatformTransactionManager.class);
		assertThat(repository).isNotNull();
		assertThat(platformTransactionManager).isNotNull();
		TransactionTemplate transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperations");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManager);
		transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperationsReadOnly");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManager);
	}

	@Test
	public void qualifiedDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				QualifiedDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("qualifiedDataSource",
				DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);
	}

	@Test
	public void primaryDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				PrimaryDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("primaryDataSource",
				DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);
	}

	@Test
	public void qualifiedAndPrimaryDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				QualifiedAndPrimaryDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		DataSource dataSource = this.context.getBean("qualifiedDataSource",
				DataSource.class);
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);
		jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperationsReadOnly");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);
	}

	@Test
	public void miassingPlatformTransactionManagerReadOnlyConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				MiassingPlatformTransactionManagerReadOnlyConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("qualifiedDataSource",
				DataSource.class);
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);
		jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperationsReadOnly");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);

		dataSource = this.context.getBean("qualifiedDataSourceReadOnly",
				DataSource.class);
		assertThat(dataSource).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isNotEqualTo(dataSource);
	}

	@Test
	public void missingDataSourceReadOnlyConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				MissingDataSourceReadOnlyConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		PlatformTransactionManager platformTransactionManager = this.context
				.getBean("transactionManager",
						PlatformTransactionManager.class);
		assertThat(repository).isNotNull();
		assertThat(platformTransactionManager).isNotNull();
		TransactionTemplate transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperations");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManager);
		transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperationsReadOnly");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManager);

		platformTransactionManager = this.context
				.getBean("qualifiedPlatformTransactionManagerReadOnly",
						PlatformTransactionManager.class);
		assertThat(platformTransactionManager).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isNotEqualTo(platformTransactionManager);
	}

	@Test
	public void qualifiedDataSourceAndTransactionManagerReadOnlyConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				QualifiedDataSourceAndTransactionManagerReadOnlyConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		assertThat(repository).isNotNull();
		DataSource dataSource = this.context.getBean("qualifiedDataSource",
				DataSource.class);
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);

		DataSource dataSourceReadOnly = this.context.getBean("qualifiedDataSourceReadOnly",
				DataSource.class);
		assertThat(dataSourceReadOnly).isNotNull();
		jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperationsReadOnly");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSourceReadOnly);

		PlatformTransactionManager platformTransactionManagerReadOnly = this.context
				.getBean("qualifiedPlatformTransactionManagerReadOnly",
				PlatformTransactionManager.class);
		assertThat(dataSourceReadOnly).isNotNull();
		TransactionTemplate transactionTemplate = (TransactionTemplate) ReflectionTestUtils
				.getField(repository, "transactionOperationsReadOnly");
		assertThat(transactionTemplate).isNotNull();
		assertThat(ReflectionTestUtils.getField(transactionTemplate, "transactionManager"))
				.isEqualTo(platformTransactionManagerReadOnly);
	}

	@Test
	public void namedDataSourceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				NamedDataSourceConfiguration.class);

		JdbcOperationsSessionRepository repository = this.context
				.getBean(JdbcOperationsSessionRepository.class);
		DataSource dataSource = this.context.getBean("dataSource", DataSource.class);
		assertThat(repository).isNotNull();
		assertThat(dataSource).isNotNull();
		JdbcOperations jdbcOperations = (JdbcOperations) ReflectionTestUtils
				.getField(repository, "jdbcOperations");
		assertThat(jdbcOperations).isNotNull();
		assertThat(ReflectionTestUtils.getField(jdbcOperations, "dataSource"))
				.isEqualTo(dataSource);
	}

	@Test
	public void multipleDataSourceConfiguration() {
		this.thrown.expect(BeanCreationException.class);
		this.thrown.expectMessage("expected single matching bean but found 2");

		registerAndRefresh(DataSourceConfiguration.class,
				MultipleDataSourceConfiguration.class);
	}

	@Test
	public void customLobHandlerConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomLobHandlerConfiguration.class);

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
		registerAndRefresh(DataSourceConfiguration.class,
				CustomConversionServiceConfiguration.class);

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
		this.context.setEnvironment(new MockEnvironment()
				.withProperty("session.jdbc.tableName", "custom_session_table"));
		registerAndRefresh(DataSourceConfiguration.class,
				CustomJdbcHttpSessionConfiguration.class);
		JdbcHttpSessionConfiguration configuration = this.context
				.getBean(JdbcHttpSessionConfiguration.class);
		assertThat(ReflectionTestUtils.getField(configuration, "tableName"))
				.isEqualTo("custom_session_table");
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
	static class CustomMaxInactiveIntervalInSecondsSetterConfiguration
			extends JdbcHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetterConfiguration() {
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@EnableJdbcHttpSession(cleanupCron = CLEANUP_CRON_EXPRESSION)
	static class CustomCleanupCronExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomCleanupCronExpressionSetterConfiguration
			extends JdbcHttpSessionConfiguration {

		CustomCleanupCronExpressionSetterConfiguration() {
			setCleanupCron(CLEANUP_CRON_EXPRESSION);
		}

	}

	@EnableJdbcHttpSession
	static class QualifiedTransactionManagerConfiguration {

		@Bean
		@SpringSessionPlatformTransactionManager
		public PlatformTransactionManager qualifiedPlatformTransactionManager() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@EnableJdbcHttpSession
	static class QualifiedTransactionManagerReadOnlyConfiguration {

		@Bean
		@SpringSessionPlatformTransactionManagerReadOnly
		public PlatformTransactionManager qualifiedPlatformTransactionReadOnlyManager() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@EnableJdbcHttpSession
	static class PrimaryTransactionManagerConfiguration {

		@Bean
		@Primary
		public PlatformTransactionManager primaryPlatformTransactionManager() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@EnableJdbcHttpSession
	static class QualifiedAndPrimaryTransactionManagerConfiguration {

		@Bean
		@SpringSessionPlatformTransactionManager
		public PlatformTransactionManager qualifiedPlatformTransactionManager() {
			return mock(PlatformTransactionManager.class);
		}

		@Bean
		@Primary
		public PlatformTransactionManager primaryPlatformTransactionManager() {
			return mock(PlatformTransactionManager.class);
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
	static class MiassingPlatformTransactionManagerReadOnlyConfiguration {

		@Bean
		@SpringSessionDataSource
		public DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		@SpringSessionDataSourceReadOnly
		public DataSource qualifiedDataSourceReadOnly() {
			return mock(DataSource.class);
		}

	}

	@EnableJdbcHttpSession
	static class MissingDataSourceReadOnlyConfiguration {

		@Bean
		@SpringSessionDataSource
		public DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		@SpringSessionPlatformTransactionManagerReadOnly
		public PlatformTransactionManager qualifiedPlatformTransactionManagerReadOnly() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@EnableJdbcHttpSession
	static class QualifiedDataSourceAndTransactionManagerReadOnlyConfiguration {

		@Bean
		@SpringSessionDataSource
		public DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		@SpringSessionDataSourceReadOnly
		public DataSource qualifiedDataSourceReadOnly() {
			return mock(DataSource.class);
		}

		@Bean
		@SpringSessionPlatformTransactionManagerReadOnly
		public PlatformTransactionManager qualifiedPlatformTransactionManagerReadOnly() {
			return mock(PlatformTransactionManager.class);
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

}
