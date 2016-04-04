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
package org.springframework.session.data.couchbase.application;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.couchbase.CouchbaseDao;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.session.data.couchbase.Serializer;
import org.springframework.session.data.couchbase.config.annotation.web.http.CouchbaseHttpSessionConfiguration;

/**
 * Integration tests configuration.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
@Configuration
public class SessionConfiguration extends CouchbaseHttpSessionConfiguration {

	/**
	 * Default HTTP session application namespace name.
	 */
	public static final String HTTP_SESSION_NAMESPACE = "test-application";

	@Bean
	public SessionRepository sessionRepository(CouchbaseDao dao, ObjectMapper mapper,
			Serializer serializer) {
		return new CouchbaseSessionRepository(dao, this.namespace, mapper,
				this.timeoutInSeconds, serializer, this.principalSessionsEnabled) {

			@Override
			protected int getSessionDocumentExpiration() {
				return timeoutInSeconds;
			}
		};
	}
}
