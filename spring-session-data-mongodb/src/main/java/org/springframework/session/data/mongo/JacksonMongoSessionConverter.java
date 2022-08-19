/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Date;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.util.Assert;

/**
 * {@code AbstractMongoSessionConverter} implementation using Jackson.
 *
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 * @author Michael Ruf
 * @since 1.2
 */
public class JacksonMongoSessionConverter extends AbstractMongoSessionConverter {

	private static final Log LOG = LogFactory.getLog(JacksonMongoSessionConverter.class);

	private static final String ATTRS_FIELD_NAME = "attrs.";

	private static final String PRINCIPAL_FIELD_NAME = "principal";

	private static final String EXPIRE_AT_FIELD_NAME = "expireAt";

	private final ObjectMapper objectMapper;

	public JacksonMongoSessionConverter() {
		this(Collections.emptyList());
	}

	public JacksonMongoSessionConverter(Iterable<Module> modules) {

		this.objectMapper = buildObjectMapper();
		this.objectMapper.registerModules(modules);
	}

	public JacksonMongoSessionConverter(ObjectMapper objectMapper) {

		Assert.notNull(objectMapper, "ObjectMapper can NOT be null!");
		this.objectMapper = objectMapper;
	}

	@Nullable
	protected Query getQueryForIndex(String indexName, Object indexValue) {

		if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Query.query(Criteria.where(PRINCIPAL_FIELD_NAME).is(indexValue));
		}
		else {
			return Query.query(Criteria.where(ATTRS_FIELD_NAME + MongoSession.coverDot(indexName)).is(indexValue));
		}
	}

	private ObjectMapper buildObjectMapper() {

		ObjectMapper objectMapper = new ObjectMapper();

		// serialize fields instead of properties
		objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

		// ignore unresolved fields (mostly 'principal')
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		objectMapper.setPropertyNamingStrategy(new MongoIdNamingStrategy());

		objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
		objectMapper.addMixIn(MongoSession.class, MongoSessionMixin.class);
		objectMapper.addMixIn(HashMap.class, HashMapMixin.class);

		return objectMapper;
	}

	@Override
	protected DBObject convert(MongoSession source) {

		try {
			DBObject dbSession = BasicDBObject.parse(this.objectMapper.writeValueAsString(source));

			// Override default serialization with proper values.
			dbSession.put(PRINCIPAL_FIELD_NAME, extractPrincipal(source));
			dbSession.put(EXPIRE_AT_FIELD_NAME, source.getExpireAt());
			return dbSession;
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Cannot convert MongoExpiringSession", ex);
		}
	}

	@Override
	@Nullable
	protected MongoSession convert(Document source) {

		Date expireAt = (Date) source.remove(EXPIRE_AT_FIELD_NAME);
		source.remove("originalSessionId");
		String json = source.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());

		try {
			MongoSession mongoSession = this.objectMapper.readValue(json, MongoSession.class);
			mongoSession.setExpireAt(expireAt);
			return mongoSession;
		}
		catch (IOException ex) {
			LOG.error("Error during Mongo Session deserialization", ex);
			return null;
		}
	}

	/**
	 * Used to whitelist {@link MongoSession} for {@link SecurityJackson2Modules}.
	 */
	private static class MongoSessionMixin {

		@JsonCreator
		MongoSessionMixin(@JsonProperty("_id") String id,
				@JsonProperty("intervalSeconds") long maxInactiveIntervalInSeconds) {
		}

	}

	/**
	 * Used to whitelist {@link HashMap} for {@link SecurityJackson2Modules}.
	 */
	private static class HashMapMixin {

		// Nothing special

	}

	private static class MongoIdNamingStrategy extends PropertyNamingStrategies.NamingBase {

		@Override
		public String translate(String propertyName) {

			switch (propertyName) {
				case "id":
					return "_id";
				case "_id":
					return "id";
				default:
					return propertyName;
			}
		}

	}

}
