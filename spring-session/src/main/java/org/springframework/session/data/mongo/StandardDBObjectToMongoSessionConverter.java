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

import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.session.Session;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Date;
import java.util.Map;

/**
 * {@code Converter} implementation which returns session deserialized from a BSON object
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
class StandardDBObjectToMongoSessionConverter implements Converter<DBObject, MongoExpiringSession> {

	private static final Log LOG = LogFactory.getLog(StandardDBObjectToMongoSessionConverter.class);

	public MongoExpiringSession convert(DBObject sessionWrapper) {
		MongoExpiringSession session =
				new MongoExpiringSession((String) sessionWrapper.get(MongoExpiringSession.ID), (Integer) sessionWrapper.get(MongoExpiringSession.MAX_INTERVAL));
		session.setCreationTime((Long) sessionWrapper.get(MongoExpiringSession.CREATION_TIME));
		session.setLastAccessedTime((Long) sessionWrapper.get(MongoExpiringSession.LAST_ACCESSED_TIME));
		session.setExpireAt((Date) sessionWrapper.get(MongoOperationsSessionRepository.EXPIRES_AT_FIELD));
		deserializeAttributes(sessionWrapper, session);
		return session;
	}

	@SuppressWarnings("unchecked")
	private void deserializeAttributes(DBObject sessionWrapper, Session session) {
		try {
			ByteArrayInputStream in = new ByteArrayInputStream((byte[]) sessionWrapper.get(MongoExpiringSession.ATTRIBUTES));
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
