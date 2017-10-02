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

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Spring @Configuration class used to configure and initialize a JDBC based HttpSession
 * provider implementation in Spring Session.
 * <p>
 * Exposes the {@link org.springframework.session.web.http.SessionRepositoryFilter} as a
 * bean named "springSessionRepositoryFilter". In order to use this a single
 * {@link DataSource} must be exposed as a Bean.
 *
 * @author Vedran Pavic
 * @author Eddú Meléndez
 * @since 1.2.0
 * @see EnableJdbcHttpSession
 */
@Configuration
@EnableScheduling
public class JdbcHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements BeanClassLoaderAware, ImportAware, EmbeddedValueResolverAware {

	private String tableName;

	private Integer maxInactiveIntervalInSeconds;

	private ClassLoader classLoader;

	private StringValueResolver embeddedValueResolver;

	@Bean
	public JdbcOperationsSessionRepository sessionRepository(
			@SpringSessionDataSource ObjectProvider<DataSource> springSessionDataSource,
			ObjectProvider<DataSource> dataSource,
			ObjectProvider<PlatformTransactionManager> transactionManager,
			@Qualifier("springSessionLobHandler") ObjectProvider<LobHandler> lobHandler,
			@Qualifier("springSessionConversionService") ObjectProvider<ConversionService> springSessionConversionService,
			@Qualifier("conversionService") ObjectProvider<ConversionService> conversionService) {
		DataSource dataSourceToUse = springSessionDataSource.getIfAvailable();
		if (dataSourceToUse == null) {
			dataSourceToUse = dataSource.getObject();
		}
		JdbcOperationsSessionRepository sessionRepository = new JdbcOperationsSessionRepository(
				dataSourceToUse, transactionManager.getObject());
		String tableName = getTableName();
		if (StringUtils.hasText(tableName)) {
			sessionRepository.setTableName(tableName);
		}
		sessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		LobHandler lobHandlerToUse = lobHandler.getIfAvailable();
		if (lobHandlerToUse != null) {
			sessionRepository.setLobHandler(lobHandlerToUse);
		}
		ConversionService conversionServiceToUse = springSessionConversionService.getIfAvailable();
		if (conversionServiceToUse == null) {
			conversionServiceToUse = conversionService.getIfAvailable();
		}
		if (conversionServiceToUse == null) {
			conversionServiceToUse = createConversionServiceWithBeanClassLoader();
		}
		sessionRepository.setConversionService(conversionServiceToUse);
		return sessionRepository;
	}

	/**
	 * This must be a separate method because some ClassLoaders load the entire method
	 * definition even if an if statement guards against it loading. This means that older
	 * versions of Spring would cause a NoSuchMethodError if this were defined in
	 * {@link #sessionRepository(ObjectProvider, ObjectProvider, ObjectProvider, ObjectProvider, ObjectProvider, ObjectProvider)}.
	 *
	 * @return the default {@link ConversionService}
	 */
	private GenericConversionService createConversionServiceWithBeanClassLoader() {
		GenericConversionService conversionService = new GenericConversionService();
		conversionService.addConverter(Object.class, byte[].class,
				new SerializingConverter());
		conversionService.addConverter(byte[].class, Object.class,
				new DeserializingConverter(this.classLoader));
		return conversionService;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
	 */
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	private String getTableName() {
		String systemProperty = System.getProperty("spring.session.jdbc.tableName", "");
		if (StringUtils.hasText(systemProperty)) {
			return systemProperty;
		}
		return this.tableName;
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableJdbcHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		String tableNameValue = enableAttrs.getString("tableName");
		if (StringUtils.hasText(tableNameValue)) {
			this.tableName = this.embeddedValueResolver
					.resolveStringValue(tableNameValue);
		}
		this.maxInactiveIntervalInSeconds = enableAttrs
				.getNumber("maxInactiveIntervalInSeconds");
	}

	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	/**
	 * Property placeholder to process the @Scheduled annotation.
	 * @return the {@link PropertySourcesPlaceholderConfigurer} to use
	 */
	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

}
