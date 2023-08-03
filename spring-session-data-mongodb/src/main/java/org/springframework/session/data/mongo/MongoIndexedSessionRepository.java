/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.mongo;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.Document;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.lang.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * Session repository implementation which stores sessions in Mongo. Uses
 * {@link AbstractMongoSessionConverter} to transform session objects from/to native Mongo
 * representation ({@code DBObject}). Repository is also responsible for removing expired
 * sessions from database. Cleanup is done every minute.
 *
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 * @author Vedran Pavic
 * @since 2.2.0
 */
public class MongoIndexedSessionRepository
		implements FindByIndexNameSessionRepository<MongoSession>, ApplicationEventPublisherAware, InitializingBean {

	/**
	 * The default time period in seconds in which a session will expire.
	 * @deprecated since 3.0.0 in favor of
	 * {@link MapSession#DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS}
	 */
	@Deprecated
	public static final int DEFAULT_INACTIVE_INTERVAL = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * the default collection name for storing session.
	 */
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private static final Log logger = LogFactory.getLog(MongoIndexedSessionRepository.class);

	private final MongoOperations mongoOperations;

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private String collectionName = DEFAULT_COLLECTION_NAME;

	private AbstractMongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter(
			this.defaultMaxInactiveInterval);

	private ApplicationEventPublisher eventPublisher;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	public MongoIndexedSessionRepository(MongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	@Override
	public MongoSession createSession() {

		MongoSession session = new MongoSession(this.sessionIdGenerator);

		session.setMaxInactiveInterval(this.defaultMaxInactiveInterval);

		publishEvent(new SessionCreatedEvent(this, session));

		return session;
	}

	@Override
	public void save(MongoSession session) {
		DBObject dbObject = MongoSessionUtils.convertToDBObject(this.mongoSessionConverter, session);
		Assert.notNull(dbObject, "dbObject must not be null");
		this.mongoOperations.save(dbObject, this.collectionName);
	}

	@Override
	@Nullable
	public MongoSession findById(String id) {

		Document sessionWrapper = findSession(id);

		if (sessionWrapper == null) {
			return null;
		}

		MongoSession session = MongoSessionUtils.convertToSession(this.mongoSessionConverter, sessionWrapper);

		if (session != null) {
			if (session.isExpired()) {
				publishEvent(new SessionExpiredEvent(this, session));
				deleteById(id);
				return null;
			}
			session.setSessionIdGenerator(this.sessionIdGenerator);
		}

		return session;
	}

	/**
	 * Currently this repository allows only querying against
	 * {@code PRINCIPAL_NAME_INDEX_NAME}.
	 * @param indexName the name if the index (i.e.
	 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
	 * @param indexValue the value of the index to search for.
	 * @return sessions map
	 */
	@Override
	public Map<String, MongoSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {

		return Optional.ofNullable(this.mongoSessionConverter.getQueryForIndex(indexName, indexValue))
				.map((query) -> this.mongoOperations.find(query, Document.class, this.collectionName))
				.orElse(Collections.emptyList()).stream()
				.map((dbSession) -> MongoSessionUtils.convertToSession(this.mongoSessionConverter, dbSession))
				.peek((session) -> session.setSessionIdGenerator(this.sessionIdGenerator))
				.collect(Collectors.toMap(MongoSession::getId, (mapSession) -> mapSession));
	}

	@Override
	public void deleteById(String id) {

		Optional.ofNullable(findSession(id)).ifPresent((document) -> {

			MongoSession session = MongoSessionUtils.convertToSession(this.mongoSessionConverter, document);
			if (session != null) {
				publishEvent(new SessionDeletedEvent(this, session));
			}
			this.mongoOperations.remove(document, this.collectionName);
		});
	}

	@Override
	public void afterPropertiesSet() {

		IndexOperations indexOperations = this.mongoOperations.indexOps(this.collectionName);
		this.mongoSessionConverter.ensureIndexes(indexOperations);
	}

	@Nullable
	private Document findSession(String id) {
		return this.mongoOperations.findById(id, Document.class, this.collectionName);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	private void publishEvent(ApplicationEvent event) {

		try {
			this.eventPublisher.publishEvent(event);
		}
		catch (Throwable ex) {
			logger.error("Error publishing " + event + ".", ex);
		}
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 30 minutes.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
		org.springframework.util.Assert.notNull(defaultMaxInactiveInterval,
				"defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval in seconds
	 * @deprecated since 3.0.0, in favor of
	 * {@link #setDefaultMaxInactiveInterval(Duration)}
	 */
	@Deprecated(since = "3.0.0")
	public void setMaxInactiveIntervalInSeconds(Integer defaultMaxInactiveInterval) {
		setDefaultMaxInactiveInterval(Duration.ofSeconds(defaultMaxInactiveInterval));
	}

	public void setCollectionName(final String collectionName) {
		this.collectionName = collectionName;
	}

	public void setMongoSessionConverter(final AbstractMongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	/**
	 * Set the {@link SessionIdGenerator} to use to generate session ids.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @since 3.2
	 */
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

}
