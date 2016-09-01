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

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

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
		implements BeanClassLoaderAware, ImportAware {

	private String tableName = "";

	private Integer maxInactiveIntervalInSeconds = 1800;

	private LobHandler lobHandler;

	@Autowired(required = false)
	@Qualifier("conversionService")
	private ConversionService conversionService;

	private ConversionService springSessionConversionService;

	private ClassLoader classLoader;

	@Bean
	public JdbcTemplate springSessionJdbcOperations(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public JdbcOperationsSessionRepository sessionRepository(
			@Qualifier("springSessionJdbcOperations") JdbcOperations jdbcOperations,
			PlatformTransactionManager transactionManager) {
		JdbcOperationsSessionRepository sessionRepository =
				new JdbcOperationsSessionRepository(jdbcOperations, transactionManager);
		String tableName = getTableName();
		if (StringUtils.hasText(tableName)) {
			sessionRepository.setTableName(tableName);
		}
		sessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		if (this.lobHandler != null) {
			sessionRepository.setLobHandler(this.lobHandler);
		}
		if (this.springSessionConversionService != null) {
			sessionRepository.setConversionService(this.springSessionConversionService);
		}
		else if (this.conversionService != null) {
			sessionRepository.setConversionService(this.conversionService);
		}
		else if (this.classLoader != null) {
			GenericConversionService conversionService = new GenericConversionService();
			conversionService.addConverter(Object.class, byte[].class,
					new SerializingConverter());
			conversionService.addConverter(byte[].class, Object.class,
					new DeserializingConverter(this.classLoader));
			sessionRepository.setConversionService(conversionService);
		}
		return sessionRepository;
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

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	private String getTableName() {
		if (StringUtils.hasText(this.tableName)) {
			return this.tableName;
		}
		return System.getProperty("spring.session.jdbc.tableName", "");
	}

	public void setBeanClassLoader(ClassLoader classLoader) {
		try {
			DeserializingConverter.class.getConstructor(ClassLoader.class);
		}
		catch (NoSuchMethodException e) {
			return;
		}
		this.classLoader = classLoader;
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableJdbcHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		this.tableName = enableAttrs.getString("tableName");
		this.maxInactiveIntervalInSeconds = enableAttrs
				.getNumber("maxInactiveIntervalInSeconds");
	}

}
