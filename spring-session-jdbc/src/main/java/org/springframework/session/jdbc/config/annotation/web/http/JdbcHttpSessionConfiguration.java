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

package org.springframework.session.jdbc.config.annotation.web.http;

import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Spring {@code @Configuration} class used to configure and initialize a JDBC based
 * {@code HttpSession} provider implementation in Spring Session.
 * <p>
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * {@code springSessionRepositoryFilter}. In order to use this a single {@link DataSource}
 * must be exposed as a Bean.
 *
 * @author Vedran Pavic
 * @author Eddú Meléndez
 * @since 1.2.0
 * @see EnableJdbcHttpSession
 */
@Configuration(proxyBeanMethods = false)
public class JdbcHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements BeanClassLoaderAware, EmbeddedValueResolverAware, ImportAware {

	static final String DEFAULT_CLEANUP_CRON = "0 * * * * *";

	private Integer maxInactiveIntervalInSeconds = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	private String tableName = JdbcIndexedSessionRepository.DEFAULT_TABLE_NAME;

	private String cleanupCron = DEFAULT_CLEANUP_CRON;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private DataSource dataSource;

	private PlatformTransactionManager transactionManager;

	private TransactionOperations transactionOperations;

	private IndexResolver<Session> indexResolver;

	private LobHandler lobHandler;

	private ConversionService springSessionConversionService;

	private ConversionService conversionService;

	private List<SessionRepositoryCustomizer<JdbcIndexedSessionRepository>> sessionRepositoryCustomizers;

	private ClassLoader classLoader;

	private StringValueResolver embeddedValueResolver;

	@Bean
	public JdbcIndexedSessionRepository sessionRepository() {
		JdbcTemplate jdbcTemplate = createJdbcTemplate(this.dataSource);
		if (this.transactionOperations == null) {
			this.transactionOperations = createTransactionTemplate(this.transactionManager);
		}
		JdbcIndexedSessionRepository sessionRepository = new JdbcIndexedSessionRepository(jdbcTemplate,
				this.transactionOperations);
		if (StringUtils.hasText(this.tableName)) {
			sessionRepository.setTableName(this.tableName);
		}
		sessionRepository.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		sessionRepository.setFlushMode(this.flushMode);
		sessionRepository.setSaveMode(this.saveMode);
		if (this.indexResolver != null) {
			sessionRepository.setIndexResolver(this.indexResolver);
		}
		if (this.lobHandler != null) {
			sessionRepository.setLobHandler(this.lobHandler);
		}
		else if (requiresTemporaryLob(this.dataSource)) {
			DefaultLobHandler lobHandler = new DefaultLobHandler();
			lobHandler.setCreateTemporaryLob(true);
			sessionRepository.setLobHandler(lobHandler);
		}
		if (this.springSessionConversionService != null) {
			sessionRepository.setConversionService(this.springSessionConversionService);
		}
		else if (this.conversionService != null) {
			sessionRepository.setConversionService(this.conversionService);
		}
		else {
			sessionRepository.setConversionService(createConversionServiceWithBeanClassLoader(this.classLoader));
		}
		this.sessionRepositoryCustomizers
				.forEach((sessionRepositoryCustomizer) -> sessionRepositoryCustomizer.customize(sessionRepository));
		return sessionRepository;
	}

	private static boolean requiresTemporaryLob(DataSource dataSource) {
		try {
			String productName = JdbcUtils.extractDatabaseMetaData(dataSource,
					DatabaseMetaData::getDatabaseProductName);
			return "Oracle".equalsIgnoreCase(JdbcUtils.commonDatabaseName(productName));
		}
		catch (MetaDataAccessException ex) {
			return false;
		}
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public void setCleanupCron(String cleanupCron) {
		this.cleanupCron = cleanupCron;
	}

	public void setFlushMode(FlushMode flushMode) {
		this.flushMode = flushMode;
	}

	public void setSaveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
	}

	@Autowired
	public void setDataSource(@SpringSessionDataSource ObjectProvider<DataSource> springSessionDataSource,
			ObjectProvider<DataSource> dataSource) {
		DataSource dataSourceToUse = springSessionDataSource.getIfAvailable();
		if (dataSourceToUse == null) {
			dataSourceToUse = dataSource.getObject();
		}
		this.dataSource = dataSourceToUse;
	}

	@Autowired
	public void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	@Autowired(required = false)
	@Qualifier("springSessionTransactionOperations")
	public void setTransactionOperations(TransactionOperations transactionOperations) {
		this.transactionOperations = transactionOperations;
	}

	@Autowired(required = false)
	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		this.indexResolver = indexResolver;
	}

	@Autowired(required = false)
	@Qualifier("springSessionLobHandler")
	public void setLobHandler(LobHandler lobHandler) {
		this.lobHandler = lobHandler;
	}

	@Autowired(required = false)
	@Qualifier("springSessionConversionService")
	public void setSpringSessionConversionService(ConversionService conversionService) {
		this.springSessionConversionService = conversionService;
	}

	@Autowired(required = false)
	@Qualifier("conversionService")
	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Autowired(required = false)
	public void setSessionRepositoryCustomizer(
			ObjectProvider<SessionRepositoryCustomizer<JdbcIndexedSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
				.getAnnotationAttributes(EnableJdbcHttpSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		String tableNameValue = attributes.getString("tableName");
		if (StringUtils.hasText(tableNameValue)) {
			this.tableName = this.embeddedValueResolver.resolveStringValue(tableNameValue);
		}
		String cleanupCron = attributes.getString("cleanupCron");
		if (StringUtils.hasText(cleanupCron)) {
			this.cleanupCron = cleanupCron;
		}
		this.flushMode = attributes.getEnum("flushMode");
		this.saveMode = attributes.getEnum("saveMode");
	}

	private static JdbcTemplate createJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.setExceptionTranslator(new SQLErrorCodeSQLExceptionTranslator(dataSource));
		jdbcTemplate.afterPropertiesSet();
		return jdbcTemplate;
	}

	private static TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		transactionTemplate.afterPropertiesSet();
		return transactionTemplate;
	}

	private static GenericConversionService createConversionServiceWithBeanClassLoader(ClassLoader classLoader) {
		GenericConversionService conversionService = new GenericConversionService();
		conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
		conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter(classLoader));
		return conversionService;
	}

	/**
	 * Configuration of scheduled job for cleaning up expired sessions.
	 */
	@EnableScheduling
	@Configuration(proxyBeanMethods = false)
	class SessionCleanupConfiguration implements SchedulingConfigurer {

		private final JdbcIndexedSessionRepository sessionRepository;

		SessionCleanupConfiguration(JdbcIndexedSessionRepository sessionRepository) {
			this.sessionRepository = sessionRepository;
		}

		@Override
		public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
			taskRegistrar.addCronTask(this.sessionRepository::cleanUpExpiredSessions,
					JdbcHttpSessionConfiguration.this.cleanupCron);
		}

	}

}
