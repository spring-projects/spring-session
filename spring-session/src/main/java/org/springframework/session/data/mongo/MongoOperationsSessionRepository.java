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
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session repository implementation which stores sessions in Mongo.
 * Uses {@link MongoSessionConverter} to transform session objects from/to
 * native Mongo representation ({@code DBObject}).
 *
 * Repository is also responsible for removing expired sessions from database.
 * Cleanup is done every minute.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public class MongoOperationsSessionRepository implements FindByIndexNameSessionRepository<MongoExpiringSession> {

	public static final int DEFAULT_INACTIVE_INTERVAL = 1800;
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private final MongoOperations mongoOperations;

	private MongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter();
	private Integer maxInactiveIntervalInSeconds = DEFAULT_INACTIVE_INTERVAL;
	private String collectionName = DEFAULT_COLLECTION_NAME;

	public MongoOperationsSessionRepository(MongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	public MongoExpiringSession createSession() {
		return new MongoExpiringSession(maxInactiveIntervalInSeconds);
	}

	public void save(MongoExpiringSession session) {
		DBObject sessionDbObject = convertToDBObject(session);
		mongoOperations.getCollection(collectionName).save(sessionDbObject);
	}

	public MongoExpiringSession getSession(String id) {
		DBObject sessionWrapper = findSession(id);
		if (sessionWrapper == null) {
			return null;
		}
		MongoExpiringSession session = convertToSession(sessionWrapper);
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
		HashMap<String, MongoExpiringSession> result = new HashMap<String, MongoExpiringSession>();
		Query query = mongoSessionConverter.getQueryForIndex(indexName, indexValue);
		if (query == null) {
			return Collections.emptyMap();
		}
		List<DBObject> mapSessions = mongoOperations.find(query, DBObject.class, collectionName);
		for (DBObject dbSession : mapSessions) {
			MongoExpiringSession mapSession = convertToSession(dbSession);
			result.put(mapSession.getId(), mapSession);
		}
		return result;
	}

	public void delete(String id) {
		mongoOperations.remove(findSession(id), collectionName);
	}

	@PostConstruct
	public void ensureIndexesAreCreated() {
		IndexOperations indexOperations = mongoOperations.indexOps(collectionName);
		mongoSessionConverter.ensureIndexes(indexOperations);
	}

	DBObject findSession(String id) {
		return mongoOperations.findById(id, DBObject.class, collectionName);
	}

	MongoExpiringSession convertToSession(DBObject session) {
		return (MongoExpiringSession) mongoSessionConverter.convert(session,
				TypeDescriptor.valueOf(DBObject.class), TypeDescriptor.valueOf(MongoExpiringSession.class));
	}

	DBObject convertToDBObject(MongoExpiringSession session) {
		return (DBObject) mongoSessionConverter.convert(session,
				TypeDescriptor.valueOf(MongoExpiringSession.class), TypeDescriptor.valueOf(DBObject.class));
	}

	public void setMongoSessionConverter(MongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}
}
