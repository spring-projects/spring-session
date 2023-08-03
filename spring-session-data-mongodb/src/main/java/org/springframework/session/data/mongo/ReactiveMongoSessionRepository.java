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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.Document;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.util.Assert;

/**
 * A {@link ReactiveSessionRepository} implementation that uses Spring Data MongoDB.
 *
 * @author Greg Turnquist
 * @author Vedran Pavic
 * @since 2.2.0
 */
public class ReactiveMongoSessionRepository
		implements ReactiveSessionRepository<MongoSession>, ApplicationEventPublisherAware, InitializingBean {

	/**
	 * The default time period in seconds in which a session will expire.
	 * @deprecated since 3.0.0 in favor of
	 * {@link MapSession#DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS}
	 */
	@Deprecated
	public static final int DEFAULT_INACTIVE_INTERVAL = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * The default collection name for storing session.
	 */
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private static final Log logger = LogFactory.getLog(ReactiveMongoSessionRepository.class);

	private final ReactiveMongoOperations mongoOperations;

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private String collectionName = DEFAULT_COLLECTION_NAME;

	private AbstractMongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter(
			this.defaultMaxInactiveInterval);

	private MongoOperations blockingMongoOperations;

	private ApplicationEventPublisher eventPublisher;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	public ReactiveMongoSessionRepository(ReactiveMongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	/**
	 * Creates a new {@link MongoSession} that is capable of being persisted by this
	 * {@link ReactiveSessionRepository}.
	 * <p>
	 * This allows optimizations and customizations in how the {@link MongoSession} is
	 * persisted. For example, the implementation returned might keep track of the changes
	 * ensuring that only the delta needs to be persisted on a save.
	 * </p>
	 * @return a new {@link MongoSession} that is capable of being persisted by this
	 * {@link ReactiveSessionRepository}
	 */
	@Override
	public Mono<MongoSession> createSession() {
		// @formatter:off
		return Mono.fromSupplier(() -> this.sessionIdGenerator.generate())
				.map(MongoSession::new)
				.doOnNext((mongoSession) -> mongoSession.setMaxInactiveInterval(this.defaultMaxInactiveInterval))
				.doOnNext(
						(mongoSession) -> mongoSession.setSessionIdGenerator(this.sessionIdGenerator))
				.doOnNext((mongoSession) -> publishEvent(new SessionCreatedEvent(this, mongoSession)))
				.switchIfEmpty(Mono.just(new MongoSession(this.sessionIdGenerator)))
				.subscribeOn(Schedulers.boundedElastic())
				.publishOn(Schedulers.parallel());
		// @formatter:on
	}

	@Override
	public Mono<Void> save(MongoSession session) {

		return Mono //
				.justOrEmpty(MongoSessionUtils.convertToDBObject(this.mongoSessionConverter, session)) //
				.flatMap((dbObject) -> {
					if (session.hasChangedSessionId()) {

						return this.mongoOperations
								.remove(Query.query(Criteria.where("_id").is(session.getOriginalSessionId())),
										this.collectionName) //
								.then(this.mongoOperations.save(dbObject, this.collectionName));
					}
					else {

						return this.mongoOperations.save(dbObject, this.collectionName);
					}
				}) //
				.then();
	}

	@Override
	public Mono<MongoSession> findById(String id) {

		return findSession(id) //
				.map((document) -> MongoSessionUtils.convertToSession(this.mongoSessionConverter, document)) //
				.filter((mongoSession) -> !mongoSession.isExpired()) //
				.doOnNext((mongoSession) -> mongoSession.setSessionIdGenerator(this.sessionIdGenerator))
				.switchIfEmpty(Mono.defer(() -> this.deleteById(id).then(Mono.empty())));
	}

	@Override
	public Mono<Void> deleteById(String id) {

		return findSession(id) //
				.flatMap((document) -> this.mongoOperations.remove(document, this.collectionName) //
						.then(Mono.just(document))) //
				.map((document) -> MongoSessionUtils.convertToSession(this.mongoSessionConverter, document)) //
				.doOnNext((mongoSession) -> publishEvent(new SessionDeletedEvent(this, mongoSession))) //
				.then();
	}

	/**
	 * Do not use
	 * {@link org.springframework.data.mongodb.core.index.ReactiveIndexOperations} to
	 * ensure indexes exist. Instead, get a blocking {@link IndexOperations} and use that
	 * instead, if possible.
	 */
	@Override
	public void afterPropertiesSet() {

		if (this.blockingMongoOperations != null) {

			IndexOperations indexOperations = this.blockingMongoOperations.indexOps(this.collectionName);
			this.mongoSessionConverter.ensureIndexes(indexOperations);
		}
	}

	private Mono<Document> findSession(String id) {
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
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
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

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(final String collectionName) {
		this.collectionName = collectionName;
	}

	public void setMongoSessionConverter(final AbstractMongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	public void setBlockingMongoOperations(final MongoOperations blockingMongoOperations) {
		this.blockingMongoOperations = blockingMongoOperations;
	}

	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

}
