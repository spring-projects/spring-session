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
package org.springframework.session.data.couchbase.application.content;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.session.data.couchbase.CouchbaseDao;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.session.data.couchbase.Serializer;

/**
 * A {@link CouchbaseSessionRepository} that immediately expires HTTP session.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class ImmediateSessionExpirationRepository extends CouchbaseSessionRepository {

	public ImmediateSessionExpirationRepository(CouchbaseDao dao, String namespace,
			ObjectMapper mapper, int sessionTimeout, Serializer serializer,
			boolean principalSessionsEnabled) {
		super(dao, namespace, mapper, sessionTimeout, serializer,
				principalSessionsEnabled);
	}

	@Override
	protected int getSessionDocumentExpiration() {
		return sessionTimeout;
	}
}
