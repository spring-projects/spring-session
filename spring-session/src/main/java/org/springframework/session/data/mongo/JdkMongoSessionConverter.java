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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * {@code AbstractMongoSessionConverter} implementation using standard Java serialization.
 *
 * @author Jakub Kubrynski
 * @author Rob Winch
 * @since 1.2
 */
public class JdkMongoSessionConverter extends AbstractMongoSessionConverter {

	private static final String ID = "_id";
	private static final String CREATION_TIME = "created";
	private static final String LAST_ACCESSED_TIME = "accessed";
	private static final String MAX_INTERVAL = "interval";
	static final String ATTRIBUTES = "attr";

	private static final String PRINCIPAL_FIELD_NAME = "principal";

	private final Converter<Object, byte[]> serializer;
	private final Converter<byte[], Object> deserializer;

	public JdkMongoSessionConverter() {
		this(new SerializingConverter(), new DeserializingConverter());
	}

	public JdkMongoSessionConverter(Converter<Object, byte[]> serializer,
			Converter<byte[], Object> deserializer) {
		Assert.notNull(serializer, "serializer cannot be null");
		Assert.notNull(deserializer, "deserializer cannot be null");
		this.serializer = serializer;
		this.deserializer = deserializer;
	}

	@Override
	public Query getQueryForIndex(String indexName, Object indexValue) {
		if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
				.equals(indexName)) {
			return Query.query(Criteria.where(PRINCIPAL_FIELD_NAME).is(indexValue));
		}
		return null;
	}

	@Override
	protected DBObject convert(MongoExpiringSession session) {
		BasicDBObject basicDBObject = new BasicDBObject();
		basicDBObject.put(ID, session.getId());
		basicDBObject.put(CREATION_TIME, session.getCreationTime());
		basicDBObject.put(LAST_ACCESSED_TIME, session.getLastAccessedTime());
		basicDBObject.put(MAX_INTERVAL, session.getMaxInactiveIntervalInSeconds());
		basicDBObject.put(PRINCIPAL_FIELD_NAME, extractPrincipal(session));
		basicDBObject.put(EXPIRE_AT_FIELD_NAME, session.getExpireAt());
		basicDBObject.put(ATTRIBUTES, serializeAttributes(session));
		return basicDBObject;
	}

	@Override
	protected MongoExpiringSession convert(DBObject sessionWrapper) {
		MongoExpiringSession session = new MongoExpiringSession(
				(String) sessionWrapper.get(ID),
				(Integer) sessionWrapper.get(MAX_INTERVAL));
		session.setCreationTime((Long) sessionWrapper.get(CREATION_TIME));
		session.setLastAccessedTime((Long) sessionWrapper.get(LAST_ACCESSED_TIME));
		session.setExpireAt((Date) sessionWrapper.get(EXPIRE_AT_FIELD_NAME));
		deserializeAttributes(sessionWrapper, session);
		return session;
	}

	private byte[] serializeAttributes(Session session) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		for (String attrName : session.getAttributeNames()) {
			attributes.put(attrName, session.getAttribute(attrName));
		}
		return this.serializer.convert(attributes);
	}

	@SuppressWarnings("unchecked")
	private void deserializeAttributes(DBObject sessionWrapper, Session session) {
		byte[] attributesBytes = (byte[]) sessionWrapper.get(ATTRIBUTES);
		Map<String, Object> attributes = (Map<String, Object>) this.deserializer
				.convert(attributesBytes);
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			session.setAttribute(entry.getKey(), entry.getValue());
		}
	}

}
