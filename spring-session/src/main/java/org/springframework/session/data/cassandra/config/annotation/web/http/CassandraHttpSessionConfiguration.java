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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.cassandra.CassandraSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

/**
 * The CassandraHttpSessionConfiguration class is a Spring @Configuration class used to
 * configure and initialize Cassandra as a
 * HttpSession provider implementation in Spring Session.
 *
 * @author John Blum
 * @since 1.1.0
 * @see EnableGemFireHttpSession
 */
@Configuration
public class CassandraHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {


	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(Object.class, byte[].class,
				new SerializingConverter());
		converter.addConverter(byte[].class, Object.class,
				new DeserializingConverter());
		return converter;
	}

	@Bean
	Cluster cluster() {
		return Cluster.builder()
				.addContactPoint("localhost").build();
	}

	@Bean
	Session session(Cluster cluster) {
		return cluster.connect("spring_session");
	}

	@Bean
	CassandraTemplate cassandraTemplate(Session session) {
		return new CassandraTemplate(session);
	}

	@Bean
	SessionRepository repository(CassandraTemplate cassandraTemplate) {
		return new CassandraSessionRepository(cassandraTemplate);
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {

	}
}
