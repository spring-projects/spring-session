/*
 * Copyright 2002-2016 the original author or authors.
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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.Session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

/**
 * {@code MongoSessionConverter} implementation transforming {@code MongoExpiringSession} to/from a BSON object
 * using standard Java serialization
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
class JdkMongoSessionConverter implements MongoSessionConverter {

	private static final Log LOG = LogFactory.getLog(JdkMongoSessionConverter.class);

	private static final String ID = "_id";
	private static final String CREATION_TIME = "created";
	private static final String LAST_ACCESSED_TIME = "accessed";
	private static final String MAX_INTERVAL = "interval";
	private static final String ATTRIBUTES = "attr";

	private static final String PRINCIPAL_FIELD_NAME = "principal";
	private static final String EXPIRES_AT_FIELD = "expireAt";
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	public Query getQueryForIndex(String indexName, Object indexValue) {
		if (PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Query.query(Criteria.where(PRINCIPAL_FIELD_NAME).is(indexValue));
		}
		return null;
	}

	/**
	 * Creates a criteria for retrieving sessions for given principal name
	 * @return built criteria
	 */

	public String getExpiresAtFieldName() {
		return EXPIRES_AT_FIELD;
	}

	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(DBObject.class, MongoExpiringSession.class));
	}

	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (source == null) {
			return null;
		}

		if (DBObject.class.isAssignableFrom(sourceType.getType())) {
			return convert((DBObject) source);
		} else {
			return convert((MongoExpiringSession) source);
		}
	}

	private DBObject convert(MongoExpiringSession session) {
		BasicDBObject basicDBObject = new BasicDBObject();
		basicDBObject.put(ID, session.getId());
		basicDBObject.put(CREATION_TIME, session.getCreationTime());
		basicDBObject.put(LAST_ACCESSED_TIME, session.getLastAccessedTime());
		basicDBObject.put(MAX_INTERVAL, session.getMaxInactiveIntervalInSeconds());
		basicDBObject.put(PRINCIPAL_FIELD_NAME, extractPrincipal(session));
		basicDBObject.put(EXPIRES_AT_FIELD, session.getExpireAt());
		basicDBObject.put(ATTRIBUTES, serializeAttributes(session));
		return basicDBObject;
	}

	private byte[] serializeAttributes(Session session) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ObjectOutputStream outputStream = new ObjectOutputStream(out);
			Map<String, Object> attributes = new HashMap<String, Object>();
			for (String attrName : session.getAttributeNames()) {
				attributes.put(attrName, session.getAttribute(attrName));
			}
			outputStream.writeObject(attributes);
			outputStream.flush();
			return out.toByteArray();
		} catch (IOException e) {
			LOG.error("Exception during session serialization", e);
			throw new IllegalStateException("Cannot serialize session", e);
		}
	}

	private String extractPrincipal(Session expiringSession) {
		String resolvedPrincipal = AuthenticationParser.extractName(expiringSession.getAttribute(SPRING_SECURITY_CONTEXT));
		if (resolvedPrincipal != null) {
			return resolvedPrincipal;
		} else {
			return expiringSession.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
		}
	}

	private MongoExpiringSession convert(DBObject sessionWrapper) {
		MongoExpiringSession session =
				new MongoExpiringSession((String) sessionWrapper.get(ID), (Integer) sessionWrapper.get(MAX_INTERVAL));
		session.setCreationTime((Long) sessionWrapper.get(CREATION_TIME));
		session.setLastAccessedTime((Long) sessionWrapper.get(LAST_ACCESSED_TIME));
		session.setExpireAt((Date) sessionWrapper.get(EXPIRES_AT_FIELD));
		deserializeAttributes(sessionWrapper, session);
		return session;
	}

	@SuppressWarnings("unchecked")
	private void deserializeAttributes(DBObject sessionWrapper, Session session) {
		try {
			ByteArrayInputStream in = new ByteArrayInputStream((byte[]) sessionWrapper.get(ATTRIBUTES));
			ObjectInputStream objectInputStream = new ObjectInputStream(in);
			Map<String, Object> attributes = (Map<String, Object>) objectInputStream.readObject();
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				session.setAttribute(entry.getKey(), entry.getValue());
			}
			objectInputStream.close();
		} catch (IOException e) {
			LOG.error("Exception during session deserialization", e);
			throw new IllegalStateException("Cannot deserialize session", e);
		} catch (ClassNotFoundException e) {
			LOG.error("Exception during session deserialization", e);
			throw new IllegalStateException("Cannot deserialize session", e);
		}
	}

}
