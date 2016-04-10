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
package org.springframework.session.data.couchbase;

import java.util.Collections;
import java.util.List;

import com.couchbase.client.java.repository.annotation.Field;

import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

/**
 * A Couchbase document used for persisting principal HTTP sessions data.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 * @see org.springframework.session.FindByIndexNameSessionRepository
 */
@Document
public class PrincipalSessionsDocument {

	@Id
	protected final String principal;
	@Field
	protected final List<String> sessionIds;

	public PrincipalSessionsDocument(String principal, List<String> sessionIds) {
		this.principal = principal;
		this.sessionIds = sessionIds;
	}

	public String getPrincipal() {
		return this.principal;
	}

	public List<String> getSessionIds() {
		return this.sessionIds == null ? Collections.<String>emptyList()
				: this.sessionIds;
	}
}
