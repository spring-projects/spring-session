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

package org.springframework.session.data.cassandra.config.annotation.web.http;

import java.util.Map;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cassandra.core.CqlOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.cassandra.CassandraSessionRepository;
import org.springframework.util.StringUtils;


/**
 * The CassandraHttpSessionConfiguration class is a Spring @Configuration class used to
 * configure and initialize Cassandra as a
 * HttpSession provider implementation in Spring Session.
 * <p>
 * Exposes the {@link org.springframework.session.web.http.SessionRepositoryFilter} as a
 * bean named "springSessionRepositoryFilter". In order to use this a single
 * {@link Session} must be exposed as a Bean.
 *
 * @author John Blum
 * @see EnableCassandraHttpSession
 * @since 1.1.0
 */
@Configuration
public class CassandraHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	private String tableName = CassandraSessionRepository.DEFAULT_TABLE_NAME;

	private Integer maxInactiveIntervalInSeconds = 1800;

	private ConsistencyLevel consistencyLevel = QueryOptions.DEFAULT_CONSISTENCY_LEVEL;

	@Bean
	public CqlOperations springSessionCqlOperations(Session session) {
		return new CassandraTemplate(session);
	}

	@Bean
	public SessionRepository repository(
			@Qualifier("springSessionCqlOperations") CqlOperations cqlOperations) {
		CassandraSessionRepository cassandraSessionRepository = new CassandraSessionRepository(cqlOperations);
		if (StringUtils.hasText(this.tableName)) {
			cassandraSessionRepository.setTableName(this.tableName);
		}
		cassandraSessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		cassandraSessionRepository.setConsistencyLevel(this.consistencyLevel);
		return cassandraSessionRepository;
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableCassandraHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		this.tableName = enableAttrs.getString("tableName");
		this.maxInactiveIntervalInSeconds = enableAttrs.getNumber("maxInactiveIntervalInSeconds");
		this.consistencyLevel = enableAttrs.getEnum("consistencyLevel");
	}
}
