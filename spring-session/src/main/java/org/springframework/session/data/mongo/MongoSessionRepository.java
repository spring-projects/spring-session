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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Session repository implementation which stores sessions in Mongo.
 * Uses {@link MongoSessionSerializer} to transform session objects from/to
 * native Mongo representation ({@code DBObject}).
 *
 * Repository is also responsible for removing expired sessions from database.
 * Cleanup is done every minute.
 *
 * @author Jakub Kubrynski
 */
public class MongoSessionRepository implements FindByIndexNameSessionRepository<ExpiringSession> {

	private static final Logger LOG = LoggerFactory.getLogger(MongoSessionRepository.class);

	private static final String ID_FIELD_NAME = "_id";

	private final MongoOperations mongoOperations;
	private final ApplicationEventPublisher eventPublisher;
	private final MongoSessionSerializer sessionSerializer;
	private final Integer maxInactiveIntervalInSeconds;
	private final String collectionName;

	public MongoSessionRepository(MongoOperations mongoOperations, ApplicationEventPublisher eventPublisher,
	                              MongoSessionSerializer mongoSessionSerializer, Integer maxInactiveIntervalInSeconds, String collectionName) {
		this.mongoOperations = mongoOperations;
		this.eventPublisher = eventPublisher;
		this.sessionSerializer = mongoSessionSerializer;
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
		this.collectionName = collectionName;
	}

	@Override
	public ExpiringSession createSession() {
		MapSession session = new MapSession(UUID.randomUUID().toString().replaceAll("-", ""));
		session.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds);
		return session;
	}

	@Override
	public void save(ExpiringSession session) {
		DBObject jo = sessionSerializer.serializeSession(session);
		mongoOperations.getCollection(collectionName).save(jo);
		eventPublisher.publishEvent(new SessionCreatedEvent(this, session.getId()));
	}

	@Override
	public ExpiringSession getSession(String id) {
		DBObject sessionWrapper = findSession(id);
		if (sessionWrapper == null) {
			return null;
		}
		ExpiringSession session = sessionSerializer.deserializeSession(sessionWrapper);
		if (session.isExpired()) {
			expireSession(id);
			return null;
		}
		return session;
	}

	/**
	 * Currently this repository allows only querying against {@code PRINCIPAL_NAME_INDEX_NAME}
	 *
	 * @param indexName  the name if the index (i.e. {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
	 * @param indexValue the value of the index to search for.
	 * @return sessions map
	 */
	@Override
	public Map<String, ExpiringSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		HashMap<String, ExpiringSession> result = new HashMap<String, ExpiringSession>();
		List<DBObject> mapSessions = mongoOperations.find(getPrincipalQuery(indexValue), DBObject.class, collectionName);
		for (DBObject session : mapSessions) {
			ExpiringSession mapSession = sessionSerializer.deserializeSession(session);
			result.put(mapSession.getId(), mapSession);
		}
		return result;
	}

	@Override
	public void delete(String id) {
		mongoOperations.remove(findSession(id), collectionName);
		eventPublisher.publishEvent(new SessionDeletedEvent(this, id));
	}

	/**
	 * Method cleanups expired sessions and emits {@code SessionExpiredEvent} event for expiring sessions
	 */
	@Scheduled(cron = "0 * * * * *")
	public void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();

		if (LOG.isDebugEnabled()) {
			LOG.debug("Cleaning up sessions expiring at ", new Date(now));
		}

		long threshold = now - TimeUnit.SECONDS.toMillis(maxInactiveIntervalInSeconds);

		List<DBObject> expiredSessions = mongoOperations.find(
				Query.query(Criteria.where(sessionSerializer.getLastAccessedFieldName()).lt(threshold)), DBObject.class, collectionName);

		for (DBObject expiredSession : expiredSessions) {
			expireSession((String) expiredSession.get(ID_FIELD_NAME));
		}
	}

	private Query getPrincipalQuery(String indexValue) {
		return Query.query(Criteria.where(sessionSerializer.getPrincipalFieldName()).is(indexValue));
	}

	DBObject findSession(String id) {
		return mongoOperations.findById(id, DBObject.class, collectionName);
	}

	private void expireSession(String id) {
		mongoOperations.remove(findSession(id), collectionName);
		eventPublisher.publishEvent(new SessionExpiredEvent(this, id));
	}
}
