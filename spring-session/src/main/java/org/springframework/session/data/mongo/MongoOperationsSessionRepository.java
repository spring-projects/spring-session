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
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session repository implementation which stores sessions in Mongo.
 * Uses {@link Converter} to transform session objects from/to
 * native Mongo representation ({@code DBObject}).
 *
 * Repository is also responsible for removing expired sessions from database.
 * Cleanup is done every minute.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public class MongoOperationsSessionRepository implements FindByIndexNameSessionRepository<MongoExpiringSession> {

	private static final Logger LOG = LoggerFactory.getLogger(MongoOperationsSessionRepository.class);

	public static final String EXPIRES_AT_FIELD = "expireAt";
	public static final String PRINCIPAL_FIELD_NAME = "principal";

	private final MongoOperations mongoOperations;
	private final Converter<MongoExpiringSession, DBObject> sessionSerializer;
	private final Converter<DBObject, MongoExpiringSession> sessionDeserializer;
	private final Integer maxInactiveIntervalInSeconds;
	private final String collectionName;

	public MongoOperationsSessionRepository(MongoOperations mongoOperations,
	                                        Converter<MongoExpiringSession, DBObject> mongoSessionSerializer,
	                                        Converter<DBObject, MongoExpiringSession> mongoSessionDeserializer,
	                                        Integer maxInactiveIntervalInSeconds, String collectionName) {
		this.mongoOperations = mongoOperations;
		this.sessionSerializer = mongoSessionSerializer;
		this.sessionDeserializer = mongoSessionDeserializer;
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
		this.collectionName = collectionName;
	}

	public MongoExpiringSession createSession() {
		return new MongoExpiringSession(maxInactiveIntervalInSeconds);
	}

	public void save(MongoExpiringSession session) {
		DBObject jo = sessionSerializer.convert(session);
		mongoOperations.getCollection(collectionName).save(jo);
	}

	public MongoExpiringSession getSession(String id) {
		DBObject sessionWrapper = findSession(id);
		if (sessionWrapper == null) {
			return null;
		}
		MongoExpiringSession session = sessionDeserializer.convert(sessionWrapper);
		if (session.isExpired()) {
			delete(id);
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
	public Map<String, MongoExpiringSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		HashMap<String, MongoExpiringSession> result = new HashMap<String, MongoExpiringSession>();
		List<DBObject> mapSessions = mongoOperations.find(getPrincipalQuery(indexValue), DBObject.class, collectionName);
		for (DBObject session : mapSessions) {
			MongoExpiringSession mapSession = sessionDeserializer.convert(session);
			result.put(mapSession.getId(), mapSession);
		}
		return result;
	}

	public void delete(String id) {
		mongoOperations.remove(findSession(id), collectionName);
	}

	/**
	 * Method ensures that there is a TTL index on {@literal expireAt} field.
	 * It's has {@literal expireAfterSeconds} set to zero seconds, so the expiration
	 * time is controlled by the application
	 */
	@PostConstruct
	public void ensureExpirationIndex() {
		IndexOperations indexOperations = mongoOperations.indexOps(collectionName);
		List<IndexInfo> indexInfo = indexOperations.getIndexInfo();
		for (IndexInfo info : indexInfo) {
			if (EXPIRES_AT_FIELD.equals(info.getName())) {
				LOG.debug("TTL index on field {} already exists", EXPIRES_AT_FIELD);
				return;
			}
		}
		LOG.info("Creating TTL index on field {}", EXPIRES_AT_FIELD);
		indexOperations.ensureIndex(new Index(EXPIRES_AT_FIELD, Sort.Direction.ASC).named(EXPIRES_AT_FIELD).expire(0));
	}

	/**
	 * Creates a query for retrieving sessions for given principal name
	 * @param indexValue principal value to query for
	 * @return built query
	 */
	protected Query getPrincipalQuery(String indexValue) {
		return Query.query(Criteria.where(PRINCIPAL_FIELD_NAME).is(indexValue));
	}

	DBObject findSession(String id) {
		return mongoOperations.findById(id, DBObject.class, collectionName);
	}

}
