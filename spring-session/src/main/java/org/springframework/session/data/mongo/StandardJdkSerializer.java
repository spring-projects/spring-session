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
import org.springframework.session.AuthenticationParser;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code MongoSessionSerializer} implementation which returns session as a BSON object with
 * all primitive elements stored as normal fields and attributes map serialized to binary form
 * using standard JDK serialization.
 * <p/>
 * The principal name is extracted from attributes or from {@code Authentication} object
 * and stored in {@literal principal} field, to allow querying against this field.
 *
 * @author Jakub Kubrynski
 */
class StandardJdkSerializer implements MongoSessionSerializer {

	private static final Log LOG = LogFactory.getLog(StandardJdkSerializer.class);

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String ID = "_id";
	private static final String CREATION_TIME = "created";
	private static final String LAST_ACCESSED_TIME = "accessed";
	private static final String MAX_INTERVAL = "maxInt";
	private static final String PRINCIPAL = "principal";
	private static final String ATTRIBUTES = "attr";

	@Override
	public DBObject serializeSession(ExpiringSession session) {
		BasicDBObject basicDBObject = new BasicDBObject();
		basicDBObject.put(ID, session.getId());
		basicDBObject.put(CREATION_TIME, session.getCreationTime());
		basicDBObject.put(LAST_ACCESSED_TIME, session.getLastAccessedTime());
		basicDBObject.put(MAX_INTERVAL, session.getMaxInactiveIntervalInSeconds());
		basicDBObject.put(PRINCIPAL, extractPrincipal(session));
		basicDBObject.put(ATTRIBUTES, serializeAttributes(session));
		return basicDBObject;
	}

	@Override
	public ExpiringSession deserializeSession(DBObject sessionWrapper) {
		MapSession mapSession = new MapSession();
		mapSession.setId((String) sessionWrapper.get(ID));
		mapSession.setCreationTime((Long) sessionWrapper.get(CREATION_TIME));
		mapSession.setLastAccessedTime((Long) sessionWrapper.get(LAST_ACCESSED_TIME));
		mapSession.setMaxInactiveIntervalInSeconds((Integer) sessionWrapper.get(MAX_INTERVAL));
		deserializeAttributes(sessionWrapper, mapSession);
		return mapSession;
	}

	@Override
	public String getPrincipalFieldName() {
		return PRINCIPAL;
	}

	@Override
	public String getLastAccessedFieldName() {
		return LAST_ACCESSED_TIME;
	}

	private byte[] serializeAttributes(ExpiringSession session) {
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

	@SuppressWarnings("unchecked")
	private void deserializeAttributes(DBObject sessionWrapper, MapSession mapSession) {
		try {
			ByteArrayInputStream in = new ByteArrayInputStream((byte[]) sessionWrapper.get(ATTRIBUTES));
			ObjectInputStream objectInputStream = new ObjectInputStream(in);
			Map<String, Object> attributes = (Map<String, Object>) objectInputStream.readObject();
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				mapSession.setAttribute(entry.getKey(), entry.getValue());
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

	private String extractPrincipal(ExpiringSession expiringSession) {
		String resolvedPrincipal = AuthenticationParser.extractName(expiringSession.getAttribute(SPRING_SECURITY_CONTEXT));
		if (resolvedPrincipal != null) {
			return resolvedPrincipal;
		} else {
			return expiringSession.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		}
	}
}
