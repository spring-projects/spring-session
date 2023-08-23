/*
 * Copyright 2014-2023 the original author or authors.
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

import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.FixedSessionIdGenerator;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
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
 */
class JdbcHttpSessionConfigurationTests {

	private static final String TABLE_NAME = "TEST_SESSION";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private static final String CLEANUP_CRON_EXPRESSION = "0 0 * * * *";

	private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void closeContext() {
		this.context.close();
	}

	@Test
	void noDataSourceConfiguration() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(NoDataSourceConfiguration.class))
				.withRootCauseInstanceOf(NoSuchBeanDefinitionException.class).havingRootCause()
				.withMessageContaining("expected at least 1 bean which qualifies as autowire candidate");
	}

	@Test
	void defaultConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, DefaultConfiguration.class);

		JdbcIndexedSessionRepository sessionRepository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository).extracting("transactionOperations")
				.hasFieldOrPropertyWithValue("propagationBehavior", TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Test
	void customTableNameAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class, CustomTableNameAnnotationConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName")).isEqualTo(TABLE_NAME);
	}

	@Test
	void customTableNameSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomTableNameSetterConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "tableName")).isEqualTo(TABLE_NAME);
	}

	@Test
	void customMaxInactiveIntervalInSecondsAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class,
				CustomMaxInactiveIntervalInSecondsAnnotationConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(repository).extracting("defaultMaxInactiveInterval")
				.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	void customMaxInactiveIntervalInSecondsSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomMaxInactiveIntervalInSecondsSetterConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(repository).extracting("defaultMaxInactiveInterval")
				.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	void customCleanupCronAnnotation() {
		registerAndRefresh(DataSourceConfiguration.class, CustomCleanupCronExpressionAnnotationConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(repository).extracting("cleanupCron").isEqualTo(CLEANUP_CRON_EXPRESSION);
	}

	@Test
	void customCleanupCronSetter() {
		registerAndRefresh(DataSourceConfiguration.class, CustomCleanupCronExpressionSetterConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(repository).extracting("cleanupCron").isEqualTo(CLEANUP_CRON_EXPRESSION);
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

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
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

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
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

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
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

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
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
				.withRootCauseInstanceOf(NoUniqueBeanDefinitionException.class).havingRootCause()
				.withMessageContaining("expected single matching bean but found 2");
	}

	@Test
	void customTransactionOperationsConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomTransactionOperationsConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		TransactionOperations transactionOperations = this.context.getBean(TransactionOperations.class);
		assertThat(repository).isNotNull();
		assertThat(transactionOperations).isNotNull();
		assertThat(repository).hasFieldOrPropertyWithValue("transactionOperations", transactionOperations);
	}

	@Test
	void customIndexResolverConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomIndexResolverConfiguration.class);
		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver = this.context.getBean(IndexResolver.class);
		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	@Test
	void customLobHandlerConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomLobHandlerConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		LobHandler lobHandler = this.context.getBean(LobHandler.class);
		assertThat(repository).isNotNull();
		assertThat(lobHandler).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "lobHandler")).isEqualTo(lobHandler);
	}

	@Test
	void customConversionServiceConfiguration() {
		registerAndRefresh(DataSourceConfiguration.class, CustomConversionServiceConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
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
		JdbcIndexedSessionRepository sessionRepository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval")
				.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	void defaultConfigurationJdbcTemplateHasExpectedExceptionTranslator() {
		registerAndRefresh(DataSourceConfiguration.class, DefaultConfiguration.class);

		JdbcIndexedSessionRepository repository = this.context.getBean(JdbcIndexedSessionRepository.class);
		JdbcTemplate jdbcTemplate = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbcOperations");
		assertThat(jdbcTemplate).isNotNull();
		assertThat(jdbcTemplate.getExceptionTranslator()).isInstanceOf(SQLErrorCodeSQLExceptionTranslator.class);
	}

	@Test
	void importConfigAndCustomize() {
		registerAndRefresh(DataSourceConfiguration.class, ImportConfigAndCustomizeConfiguration.class);
		JdbcIndexedSessionRepository sessionRepository = this.context.getBean(JdbcIndexedSessionRepository.class);
		assertThat(sessionRepository).extracting("defaultMaxInactiveInterval").isEqualTo(Duration.ZERO);
	}

	@Test
	void sessionIdGeneratorWhenCustomBeanThenUses() {
		registerAndRefresh(DataSourceConfiguration.class, CustomSessionIdGeneratorConfiguration.class);
		JdbcIndexedSessionRepository sessionRepository = this.context.getBean(JdbcIndexedSessionRepository.class);
		SessionIdGenerator sessionIdGenerator = (SessionIdGenerator) ReflectionTestUtils.getField(sessionRepository,
				"sessionIdGenerator");
		assertThat(sessionIdGenerator).isInstanceOf(FixedSessionIdGenerator.class);
	}

	@Test
	void sessionIdGeneratorWhenNoBeanThenDefault() {
		registerAndRefresh(DataSourceConfiguration.class, DefaultConfiguration.class);
		JdbcIndexedSessionRepository sessionRepository = this.context.getBean(JdbcIndexedSessionRepository.class);
		SessionIdGenerator sessionIdGenerator = (SessionIdGenerator) ReflectionTestUtils.getField(sessionRepository,
				"sessionIdGenerator");
		assertThat(sessionIdGenerator).isInstanceOf(UuidSessionIdGenerator.class);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class CustomSessionIdGeneratorConfiguration {

		@Bean
		SessionIdGenerator sessionIdGenerator() {
			return new FixedSessionIdGenerator("my-id");
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class NoDataSourceConfiguration {

	}

	@Configuration
	static class DataSourceConfiguration {

		@Bean
		DataSource defaultDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		PlatformTransactionManager transactionManager() {
			return mock(PlatformTransactionManager.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class DefaultConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession(tableName = TABLE_NAME)
	static class CustomTableNameAnnotationConfiguration {

	}

	@Configuration
	static class CustomTableNameSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomTableNameSetterConfiguration() {
			setTableName(TABLE_NAME);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsAnnotationConfiguration {

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetterConfiguration() {
			setMaxInactiveInterval(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession(cleanupCron = CLEANUP_CRON_EXPRESSION)
	static class CustomCleanupCronExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomCleanupCronExpressionSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomCleanupCronExpressionSetterConfiguration() {
			setCleanupCron(CLEANUP_CRON_EXPRESSION);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession(flushMode = FlushMode.IMMEDIATE)
	static class CustomFlushModeExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomFlushModeExpressionSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomFlushModeExpressionSetterConfiguration() {
			setFlushMode(FlushMode.IMMEDIATE);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession(saveMode = SaveMode.ALWAYS)
	static class CustomSaveModeExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomSaveModeExpressionSetterConfiguration extends JdbcHttpSessionConfiguration {

		CustomSaveModeExpressionSetterConfiguration() {
			setSaveMode(SaveMode.ALWAYS);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class QualifiedDataSourceConfiguration {

		@Bean
		@SpringSessionDataSource
		DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class PrimaryDataSourceConfiguration {

		@Bean
		@Primary
		DataSource primaryDataSource() {
			return mock(DataSource.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class QualifiedAndPrimaryDataSourceConfiguration {

		@Bean
		@SpringSessionDataSource
		DataSource qualifiedDataSource() {
			return mock(DataSource.class);
		}

		@Bean
		@Primary
		DataSource primaryDataSource() {
			return mock(DataSource.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class NamedDataSourceConfiguration {

		@Bean
		DataSource dataSource() {
			return mock(DataSource.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class MultipleDataSourceConfiguration {

		@Bean
		DataSource secondaryDataSource() {
			return mock(DataSource.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class CustomTransactionOperationsConfiguration {

		@Bean
		TransactionOperations springSessionTransactionOperations() {
			return TransactionOperations.withoutTransaction();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class CustomIndexResolverConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class CustomLobHandlerConfiguration {

		@Bean
		LobHandler springSessionLobHandler() {
			return mock(LobHandler.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class CustomConversionServiceConfiguration {

		@Bean
		ConversionService springSessionConversionService() {
			return mock(ConversionService.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession(tableName = "${session.jdbc.tableName}")
	static class CustomJdbcHttpSessionConfiguration {

		@Bean
		PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJdbcHttpSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		@Order(0)
		SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

		@Bean
		@Order(1)
		SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository
					.setDefaultMaxInactiveInterval(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(JdbcHttpSessionConfiguration.class)
	static class ImportConfigAndCustomizeConfiguration {

		@Bean
		SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionRepositoryCustomizer() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(Duration.ZERO);
		}

	}

}
