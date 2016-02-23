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
import org.springframework.core.convert.converter.Converter;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code Converter} implementation which returns session as a BSON object with
 * all primitive elements stored as normal fields and attributes map serialized to binary form
 * using standard JDK serialization.
 * <p/>
 * The principal name is extracted from attributes or from {@code Authentication} object
 * and stored in {@literal principal} field, to allow querying against this field.
 *
 * @author Jakub Kubrynski
 */
class StandardMongoSessionToDBObjectConverter implements Converter<MongoExpiringSession, DBObject> {

	private static final Log LOG = LogFactory.getLog(StandardMongoSessionToDBObjectConverter.class);

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";
	private static final String NAME_EXPRESSION = "authentication?.name";

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	public DBObject convert(MongoExpiringSession session) {
		BasicDBObject basicDBObject = new BasicDBObject();
		basicDBObject.put(MongoExpiringSession.ID, session.getId());
		basicDBObject.put(MongoExpiringSession.CREATION_TIME, session.getCreationTime());
		basicDBObject.put(MongoExpiringSession.LAST_ACCESSED_TIME, session.getLastAccessedTime());
		basicDBObject.put(MongoExpiringSession.MAX_INTERVAL, session.getMaxInactiveIntervalInSeconds());
		basicDBObject.put(MongoOperationsSessionRepository.PRINCIPAL_FIELD_NAME, extractPrincipal(session));
		basicDBObject.put(MongoOperationsSessionRepository.EXPIRES_AT_FIELD, session.getExpireAt());
		basicDBObject.put(MongoExpiringSession.ATTRIBUTES, serializeAttributes(session));
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
		String resolvedPrincipal = extractNameFromAuth(expiringSession.getAttribute(SPRING_SECURITY_CONTEXT));
		if (resolvedPrincipal != null) {
			return resolvedPrincipal;
		} else {
			return expiringSession.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		}
	}

	/**
	 * Extracts principal name from authentication
	 *
	 * @param authentication Authentication object
	 * @return principal name
	 */
	private String extractNameFromAuth(Object authentication) {
		if (authentication != null) {
			Expression expression = PARSER.parseExpression(NAME_EXPRESSION);
			return expression.getValue(authentication, String.class);
		}
		return null;
	}

}
