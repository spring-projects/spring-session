/*
 * Copyright 2014-present the original author or authors.
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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.Document;
import org.bson.types.Binary;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * {@code AbstractMongoSessionConverter} implementation using standard Java serialization.
 *
 * @author Jakub Kubrynski
 * @author Rob Winch
 * @author Greg Turnquist
 * @since 1.2
 */
public class JdkMongoSessionConverter extends AbstractMongoSessionConverter {

	private static final String ID = "_id";

	private static final String CREATION_TIME = "created";

	private static final String LAST_ACCESSED_TIME = "accessed";

	private static final String MAX_INTERVAL = "interval";

	private static final String ATTRIBUTES = "attr";

	private static final String PRINCIPAL_FIELD_NAME = "principal";

	private final Converter<Object, byte[]> serializer;

	private final Converter<byte[], Object> deserializer;

	private Duration maxInactiveInterval;

	public JdkMongoSessionConverter(Duration maxInactiveInterval) {
		this(new SerializingConverter(), new DeserializingConverter(), maxInactiveInterval);
	}

	public JdkMongoSessionConverter(Converter<Object, byte[]> serializer, Converter<byte[], Object> deserializer,
			Duration maxInactiveInterval) {

		Assert.notNull(serializer, "serializer cannot be null");
		Assert.notNull(deserializer, "deserializer cannot be null");
		Assert.notNull(maxInactiveInterval, "maxInactiveInterval cannot be null");

		this.serializer = serializer;
		this.deserializer = deserializer;
		this.maxInactiveInterval = maxInactiveInterval;
	}

	@Override
	@Nullable
	public Query getQueryForIndex(String indexName, Object indexValue) {

		if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Query.query(Criteria.where(PRINCIPAL_FIELD_NAME).is(indexValue));
		}
		else {
			return null;
		}
	}

	@Override
	protected DBObject convert(MongoSession session) {

		BasicDBObject basicDBObject = new BasicDBObject();

		basicDBObject.put(ID, session.getId());
		basicDBObject.put(CREATION_TIME, session.getCreationTime());
		basicDBObject.put(LAST_ACCESSED_TIME, session.getLastAccessedTime());
		basicDBObject.put(MAX_INTERVAL, session.getMaxInactiveInterval());
		basicDBObject.put(PRINCIPAL_FIELD_NAME, extractPrincipal(session));
		basicDBObject.put(EXPIRE_AT_FIELD_NAME, session.getExpireAt());
		basicDBObject.put(ATTRIBUTES, serializeAttributes(session));

		return basicDBObject;
	}

	@Override
	protected MongoSession convert(Document sessionWrapper) {

		Object maxInterval = sessionWrapper.getOrDefault(MAX_INTERVAL, this.maxInactiveInterval);

		Duration maxIntervalDuration = (maxInterval instanceof Duration) ? (Duration) maxInterval
				: Duration.parse(maxInterval.toString());

		MongoSession session = new MongoSession(sessionWrapper.getString(ID), maxIntervalDuration.getSeconds());

		Object creationTime = sessionWrapper.get(CREATION_TIME);
		if (creationTime instanceof Instant) {
			session.setCreationTime(((Instant) creationTime).toEpochMilli());
		}
		else if (creationTime instanceof Date) {
			session.setCreationTime(((Date) creationTime).getTime());
		}

		Object lastAccessedTime = sessionWrapper.get(LAST_ACCESSED_TIME);
		if (lastAccessedTime instanceof Instant) {
			session.setLastAccessedTime((Instant) lastAccessedTime);
		}
		else if (lastAccessedTime instanceof Date) {
			session.setLastAccessedTime(Instant.ofEpochMilli(((Date) lastAccessedTime).getTime()));
		}

		session.setExpireAt((Date) sessionWrapper.get(EXPIRE_AT_FIELD_NAME));

		deserializeAttributes(sessionWrapper, session);

		return session;
	}

	@Nullable
	private byte[] serializeAttributes(Session session) {

		Map<String, Object> attributes = new HashMap<>();

		for (String attrName : session.getAttributeNames()) {
			attributes.put(attrName, session.getAttribute(attrName));
		}

		return this.serializer.convert(attributes);
	}

	@SuppressWarnings("unchecked")
	private void deserializeAttributes(Document sessionWrapper, Session session) {

		Object sessionAttributes = sessionWrapper.get(ATTRIBUTES);

		byte[] attributesBytes = ((sessionAttributes instanceof Binary) ? ((Binary) sessionAttributes).getData()
				: (byte[]) sessionAttributes);

		Map<String, Object> attributes = (Map<String, Object>) this.deserializer.convert(attributesBytes);

		if (attributes != null) {
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				session.setAttribute(entry.getKey(), entry.getValue());
			}
		}
	}

}
