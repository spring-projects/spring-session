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

import java.util.Map;

import com.couchbase.client.java.repository.annotation.Field;

import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

@Document
public class SessionDocument {

	@Id
	protected final String id;
	@Field
	protected final Map<String, Map<String, Object>> data;

	public SessionDocument(String id, Map<String, Map<String, Object>> data) {
		this.id = id;
		this.data = data;
	}

	public String getId() {
		return this.id;
	}

	public Map<String, Map<String, Object>> getData() {
		return this.data;
	}
}
