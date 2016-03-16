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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.DefaultSerializer;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * {@code AbstractMongoSessionConverter} implementation using standard Java serialization.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public class JdkMongoSessionConverter extends AbstractMongoSessionConverter {

	private static final Log LOG = LogFactory.getLog(JdkMongoSessionConverter.class);

	private static final String ID = "_id";
	private static final String CREATION_TIME = "created";
	private static final String LAST_ACCESSED_TIME = "accessed";
	private static final String MAX_INTERVAL = "interval";
	static final String ATTRIBUTES = "attr";

	private static final String PRINCIPAL_FIELD_NAME = "principal";

	private final Serializer serializer;
	private final Deserializer deserializer;

	public JdkMongoSessionConverter() {
		this(new DefaultSerializer(), new DefaultDeserializer());
	}

	public JdkMongoSessionConverter(Serializer serializer, Deserializer deserializer) {
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

	@SuppressWarnings("unchecked")
	private byte[] serializeAttributes(Session session) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Map<String, Object> attributes = new HashMap<String, Object>();
			for (String attrName : session.getAttributeNames()) {
				attributes.put(attrName, session.getAttribute(attrName));
			}
			this.serializer.serialize(attributes, out);
			return out.toByteArray();
		}
		catch (IOException e) {
			LOG.error("Exception during session serialization", e);
			throw new IllegalStateException("Cannot serialize session", e);
		}
	}

	@SuppressWarnings("unchecked")
	private void deserializeAttributes(DBObject sessionWrapper, Session session) {
		try {
			ByteArrayInputStream in = new ByteArrayInputStream(
					(byte[]) sessionWrapper.get(ATTRIBUTES));
			Map<String, Object> attributes =
					(Map<String, Object>) this.deserializer.deserialize(in);
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				session.setAttribute(entry.getKey(), entry.getValue());
			}
		}
		catch (IOException e) {
			LOG.error("Exception during session deserialization", e);
			throw new IllegalStateException("Cannot deserialize session", e);
		}
	}

}
