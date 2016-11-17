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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.cassandra.CassandraSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
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
 * @see EnableGemFireHttpSession
 * @since 1.1.0
 */
@Configuration
public class CassandraHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	private String keyspace = "";

	private String[] contactPoints = new String[]{"localhost"};

	private int port = 9042;

	private String tableName = CassandraSessionRepository.DEFAULT_TABLE_NAME;

	private Integer maxInactiveIntervalInSeconds = 1800;

	@Bean
	public Cluster springSessionCassandraCluster() {
		return Cluster.builder()
				.addContactPoints(this.contactPoints)
				.withPort(this.port)
				.build();
	}

	@Bean
	public Session springSessionCassandraDatastaxDriverSession(
			@Qualifier("springSessionCassandraCluster") Cluster cluster) {
		if (!StringUtils.isEmpty(this.keyspace)) {
			return cluster.connect(this.keyspace);
		} else {
			return cluster.connect();
		}
	}

	@Bean
	public CassandraOperations springSessionCassandraOperations(
			@Qualifier("springSessionCassandraDatastaxDriverSession") Session session) {
		return new CassandraTemplate(session);
	}

	@Bean
	public SessionRepository repository(
			@Qualifier("springSessionCassandraOperations") CassandraOperations cassandraOperations) {
		CassandraSessionRepository cassandraSessionRepository = new CassandraSessionRepository(cassandraOperations);
		if (StringUtils.hasText(this.tableName)) {
			cassandraSessionRepository.setTableName(this.tableName);
		}
		cassandraSessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		return cassandraSessionRepository;
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableCassandraHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		this.tableName = enableAttrs.getString("tableName");
		this.maxInactiveIntervalInSeconds = enableAttrs.getNumber("maxInactiveIntervalInSeconds");
		this.keyspace = enableAttrs.getString("keyspace");
		this.contactPoints = enableAttrs.getStringArray("contactPoints");
		this.port = enableAttrs.getNumber("port");
	}
}
