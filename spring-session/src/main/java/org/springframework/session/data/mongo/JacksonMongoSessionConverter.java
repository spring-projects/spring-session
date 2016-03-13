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
package org.springframework.session.data.mongo;

import java.io.IOException;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;

/**
 * {@code AbstractMongoSessionConverter} implementation using Jackson.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public class JacksonMongoSessionConverter extends AbstractMongoSessionConverter {

	private static final Log LOG = LogFactory.getLog(JacksonMongoSessionConverter.class);

	private static final String ATTRS_FIELD_NAME = "attrs.";
	private static final String PRINCIPAL_FIELD_NAME = "principal";

	private final ObjectMapper objectMapper;

	public JacksonMongoSessionConverter() {
		this(Collections.<Module>emptyList());
	}

	public JacksonMongoSessionConverter(Iterable<Module> modules) {
		this.objectMapper = buildObjectMapper();
		this.objectMapper.registerModules(modules);
	}

	protected Query getQueryForIndex(String indexName, Object indexValue) {
		if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
				.equals(indexName)) {
			return Query.query(Criteria.where(PRINCIPAL_FIELD_NAME).is(indexValue));
		}
		return Query.query(Criteria.where(ATTRS_FIELD_NAME +
				MongoExpiringSession.coverDot(indexName)).is(indexValue));
	}

	private ObjectMapper buildObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();

		// serialize fields instead of properties
		objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

		// ignore unresolved fields (mostly 'principal')
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		objectMapper.setPropertyNamingStrategy(new MongoIdNamingStrategy());
		return objectMapper;
	}

	@Override
	protected DBObject convert(MongoExpiringSession source) {
		try {
			DBObject dbSession = (DBObject) JSON.parse(this.objectMapper.writeValueAsString(source));
			dbSession.put(PRINCIPAL_FIELD_NAME, extractPrincipal(source));
			return dbSession;
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot convert MongoExpiringSession", e);
		}
	}

	@Override
	protected MongoExpiringSession convert(DBObject source) {
		String json = JSON.serialize(source);
		try {
			return this.objectMapper.readValue(json, MongoExpiringSession.class);
		}
		catch (IOException e) {
			LOG.error("Error during Mongo Session deserialization", e);
			return null;
		}
	}

	private static class MongoIdNamingStrategy extends PropertyNamingStrategy.PropertyNamingStrategyBase {

		@Override
		public String translate(String propertyName) {
			if (propertyName.equals("id")) {
				return "_id";
			}
			else if (propertyName.equals("_id")) {
				return "id";
			}
			return propertyName;
		}
	}
}
