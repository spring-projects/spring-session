/*
 * Copyright 2014-2017 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.mongodb.DBObject;

import org.bson.Document;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;

/**
 * Session repository implementation which stores sessions in Mongo. Uses
 * {@link AbstractMongoSessionConverter} to transform session objects from/to native Mongo
 * representation ({@code DBObject}).
 *
 * Repository is also responsible for removing expired sessions from database. Cleanup is
 * done every minute.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public class MongoOperationsSessionRepository
		implements FindByIndexNameSessionRepository<MongoExpiringSession> {

	/**
	 * The default time period in seconds in which a session will expire.
	 */
	public static final int DEFAULT_INACTIVE_INTERVAL = 1800;

	/**
	 * the default collection name for storing session.
	 */
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private final MongoOperations mongoOperations;

	private AbstractMongoSessionConverter mongoSessionConverter =
		SessionConverterProvider.getDefaultMongoConverter();

	private Integer maxInactiveIntervalInSeconds = DEFAULT_INACTIVE_INTERVAL;
	private String collectionName = DEFAULT_COLLECTION_NAME;

	public MongoOperationsSessionRepository(MongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	public MongoExpiringSession createSession() {
		MongoExpiringSession session = new MongoExpiringSession();
		if (this.maxInactiveIntervalInSeconds != null) {
			session.setMaxInactiveIntervalInSeconds(this.maxInactiveIntervalInSeconds);
		}
		return session;
	}

	public void save(MongoExpiringSession session) {
		DBObject sessionDbObject = convertToDBObject(session);
		this.mongoOperations.save(sessionDbObject, this.collectionName);
	}

	public MongoExpiringSession getSession(String id) {
		Document sessionWrapper = findSession(id);
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
	 * Currently this repository allows only querying against
	 * {@code PRINCIPAL_NAME_INDEX_NAME}.
	 *
	 * @param indexName the name if the index (i.e.
	 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
	 * @param indexValue the value of the index to search for.
	 * @return sessions map
	 */
	public Map<String, MongoExpiringSession> findByIndexNameAndIndexValue(
			String indexName, String indexValue) {
		HashMap<String, MongoExpiringSession> result = new HashMap<String, MongoExpiringSession>();
		Query query = this.mongoSessionConverter.getQueryForIndex(indexName, indexValue);
		if (query == null) {
			return Collections.emptyMap();
		}
		List<Document> mapSessions = this.mongoOperations.find(query, Document.class, this.collectionName);
		for (Document dbSession : mapSessions) {
			MongoExpiringSession mapSession = convertToSession(dbSession);
			result.put(mapSession.getId(), mapSession);
		}
		return result;
	}

	public void delete(String id) {
		this.mongoOperations.remove(findSession(id), this.collectionName);
	}

	@PostConstruct
	public void ensureIndexesAreCreated() {
		IndexOperations indexOperations = this.mongoOperations
				.indexOps(this.collectionName);
		this.mongoSessionConverter.ensureIndexes(indexOperations);
	}

	Document findSession(String id) {
		return this.mongoOperations.findById(id, Document.class, this.collectionName);
	}

	MongoExpiringSession convertToSession(Document session) {
		return (MongoExpiringSession) this.mongoSessionConverter.convert(session,
				TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoExpiringSession.class));
	}

	DBObject convertToDBObject(MongoExpiringSession session) {
		return (DBObject) this.mongoSessionConverter.convert(session,
				TypeDescriptor.valueOf(MongoExpiringSession.class),
				TypeDescriptor.valueOf(DBObject.class));
	}

	public void setMongoSessionConverter(
			AbstractMongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}
}
