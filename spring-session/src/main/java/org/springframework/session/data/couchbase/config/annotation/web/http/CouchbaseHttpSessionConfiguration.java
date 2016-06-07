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
package org.springframework.session.data.couchbase.config.annotation.web.http;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.couchbase.CouchbaseDao;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.session.data.couchbase.SessionDataConverter;

/**
 * Couchbase backed HTTP session configuration.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
@Configuration
@EnableSpringHttpSession
public class CouchbaseHttpSessionConfiguration implements ImportAware {

	protected int timeoutInSeconds;
	protected boolean principalSessionsEnabled;

	@Bean
	public CouchbaseDao couchbaseDao(CouchbaseTemplate couchbase) {
		return new CouchbaseDao(couchbase);
	}

	@Bean
	public SessionDataConverter sessionDataConverter() {
		return new SessionDataConverter();
	}

	@Bean
	public SessionRepository sessionRepository(CouchbaseDao dao, ObjectMapper mapper, SessionDataConverter converter) {
		return new CouchbaseSessionRepository(dao, mapper, this.timeoutInSeconds, converter, this.principalSessionsEnabled);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributesByNames = importMetadata
				.getAnnotationAttributes(EnableCouchbaseHttpSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributesByNames);
		this.timeoutInSeconds = attributes.getNumber("timeoutInSeconds");
		this.principalSessionsEnabled = attributes.getBoolean("principalSessionsEnabled");
	}

	public void setTimeoutInSeconds(int timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}

	public void setPrincipalSessionsEnabled(boolean principalSessionsEnabled) {
		this.principalSessionsEnabled = principalSessionsEnabled;
	}
}
