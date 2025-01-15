/*
 * Copyright 2014-2023 the original author or authors.
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

package org.springframework.session.data.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link ReactiveSessionRepository} that is implemented using Spring Data's
 * {@link ReactiveRedisOperations}.
 *
 * <h2>Storage Details</h2> The sections below outline how Redis is updated for each
 * operation. An example of creating a new session can be found below. The subsequent
 * sections describe the details.
 *
 * <pre>
 * HMSET spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381 creationTime 1702400400000 maxInactiveInterval 1800 lastAccessedTime 1702400400000 sessionAttr:attrName someAttrValue sessionAttr:attrName2 someAttrValue2
 * EXPIRE spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381 2100
 * APPEND spring:session:sessions:expires:648377f7-c76f-4f45-b847-c0268bb48381 ""
 * EXPIRE spring:session:sessions:expires:648377f7-c76f-4f45-b847-c0268bb48381 1800
 * ZADD spring:session:sessions:expirations "1.702402961162E12" "648377f7-c76f-4f45-b847-c0268bb48381"
 * SADD spring:session:sessions:index:PRINCIPAL_NAME_INDEX_NAME:user "648377f7-c76f-4f45-b847-c0268bb48381"
 * SADD spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381:idx "spring:session:sessions:index:PRINCIPAL_NAME_INDEX_NAME:user"
 * </pre>
 *
 * <h3>Saving a Session</h3>
 *
 * <p>
 * Each session is stored in Redis as a
 * <a href="https://redis.io/topics/data-types#hashes">Hash</a>. Each session is set and
 * updated using the <a href="https://redis.io/commands/hmset">HMSET command</a>. An
 * example of how each session is stored can be seen below.
 * </p>
 *
 * <pre>
 * HMSET spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381 creationTime 1702400400000 maxInactiveInterval 1800 lastAccessedTime 1702400400000 sessionAttr:attrName someAttrValue sessionAttr:attrName2 someAttrValue2
 * </pre>
 *
 * <p>
 * In this example, the session following statements are true about the session:
 * </p>
 * <ul>
 * <li>The session id is 648377f7-c76f-4f45-b847-c0268bb48381</li>
 * <li>The session was created at 1702400400000 in milliseconds since midnight of 1/1/1970
 * GMT.</li>
 * <li>The session expires in 1800 seconds (30 minutes).</li>
 * <li>The session was last accessed at 1702400400000 in milliseconds since midnight of
 * 1/1/1970 GMT.</li>
 * <li>The session has two attributes. The first is "attrName" with the value of
 * "someAttrValue". The second session attribute is named "attrName2" with the value of
 * "someAttrValue2".</li>
 * </ul>
 *
 * <h3>Optimized Writes</h3>
 *
 * <p>
 * The {@link ReactiveRedisIndexedSessionRepository.RedisSession} keeps track of the
 * properties that have changed and only updates those. This means if an attribute is
 * written once and read many times we only need to write that attribute once. For
 * example, assume the session attribute "attrName2" from earlier was updated. The
 * following would be executed upon saving:
 * </p>
 *
 * <pre>
 * HMSET spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381 sessionAttr:attrName2 newValue
 * </pre>
 *
 * <h3>SessionCreatedEvent</h3>
 *
 * <p>
 * When a session is created an event is sent to Redis with the channel of
 * "spring:session:event:0:created:648377f7-c76f-4f45-b847-c0268bb48381" such that
 * "648377f7-c76f-4f45-b847-c0268bb48381" is the session id. The body of the event will be
 * the session that was created.
 * </p>
 *
 * <h3>SessionDeletedEvent and SessionExpiredEvent</h3> If you configured you Redis server
 * to send keyspace events when keys are expired or deleted, either via
 * {@link org.springframework.session.data.redis.config.annotation.ConfigureNotifyKeyspaceEventsReactiveAction}
 * or via external configuration, then deleted and expired sessions will be published as
 * {@link SessionDeletedEvent} and {@link SessionExpiredEvent} respectively.
 *
 * <h3>Expiration</h3>
 *
 * <p>
 * An expiration is associated to each session using the
 * <a href="https://redis.io/commands/expire">EXPIRE command</a> based upon the
 * {@link ReactiveRedisIndexedSessionRepository.RedisSession#getMaxInactiveInterval()} .
 * For example:
 * </p>
 *
 * <pre>
 * EXPIRE spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381 2100
 * </pre>
 *
 * <p>
 * You will note that the expiration that is set is 5 minutes after the session actually
 * expires. This is necessary so that the value of the session can be accessed when the
 * session expires. An expiration is set on the session itself five minutes after it
 * actually expires to ensure it is cleaned up, but only after we perform any necessary
 * processing.
 * </p>
 *
 * <p>
 * <b>NOTE:</b> The {@link #findById(String)} method ensures that no expired sessions will
 * be returned. This means there is no need to check the expiration before using a
 * session.
 * </p>
 *
 * <p>
 * Spring Session relies on the expired and delete
 * <a href="https://redis.io/docs/manual/keyspace-notifications/">keyspace
 * notifications</a> from Redis to fire a SessionDestroyedEvent. It is the
 * SessionDestroyedEvent that ensures resources associated with the Session are cleaned
 * up. For example, when using Spring Session's WebSocket support the Redis expired or
 * delete event is what triggers any WebSocket connections associated with the session to
 * be closed.
 * </p>
 *
 * <p>
 * Expiration is not tracked directly on the session key itself since this would mean the
 * session data would no longer be available. Instead a special session expires key is
 * used. In our example the expires key is:
 * </p>
 *
 * <pre>
 * APPEND spring:session:sessions:expires:648377f7-c76f-4f45-b847-c0268bb48381 ""
 * EXPIRE spring:session:sessions:expires:648377f7-c76f-4f45-b847-c0268bb48381 1800
 * </pre>
 *
 * <p>
 * When a session key is deleted or expires, the keyspace notification triggers a lookup
 * of the actual session and a {@link SessionDestroyedEvent} is fired.
 * </p>
 *
 * <p>
 * One problem with relying on Redis expiration exclusively is that Redis makes no
 * guarantee of when the expired event will be fired if the key has not been accessed. For
 * additional details see <a href="https://redis.io/commands/expire/">How Redis expires
 * keys</a> section in the Redis Expire documentation.
 * </p>
 *
 * <p>
 * To circumvent the fact that expired events are not guaranteed to happen we can ensure
 * that each key is accessed when it is expected to expire. This means that if the TTL is
 * expired on the key, Redis will remove the key and fire the expired event when we try to
 * access the key.
 * </p>
 *
 * <p>
 * For this reason, each session expiration is also tracked by storing the session id in a
 * sorted set ranked by its expiration time. This allows a background task to access the
 * potentially expired sessions to ensure that Redis expired events are fired in a more
 * deterministic fashion. For example:
 * </p>
 *
 * <pre>
 * ZADD spring:session:sessions:expirations "1.702402961162E12" "648377f7-c76f-4f45-b847-c0268bb48381"
 * </pre>
 *
 * <p>
 * <b>NOTE</b>: We do not explicitly delete the keys since in some instances there may be
 * a race condition that incorrectly identifies a key as expired when it is not. Short of
 * using distributed locks (which would kill our performance) there is no way to ensure
 * the consistency of the expiration mapping. By simply accessing the key, we ensure that
 * the key is only removed if the TTL on that key is expired.
 * </p>
 *
 * <h3>Secondary Indexes</h3> By default, Spring Session will also index the sessions by
 * identifying if the session contains any attribute that can be mapped to a principal
 * using an {@link org.springframework.session.PrincipalNameIndexResolver}. All resolved
 * indexes for a session are stored in a Redis Set, for example: <pre>
 * SADD spring:session:sessions:index:PRINCIPAL_NAME_INDEX_NAME:user "648377f7-c76f-4f45-b847-c0268bb48381"
 * SADD spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381:idx "spring:session:sessions:index:PRINCIPAL_NAME_INDEX_NAME:user"
 * </pre>
 *
 * Therefore, you can check all indexes for a given session by getting the members of the
 * {@code "spring:session:sessions:648377f7-c76f-4f45-b847-c0268bb48381:idx"} Redis set.
 *
 * @author Marcus da Coregio
 * @since 3.3
 */
public class ReactiveRedisIndexedSessionRepository
		implements ReactiveSessionRepository<ReactiveRedisIndexedSessionRepository.RedisSession>,
		ReactiveFindByIndexNameSessionRepository<ReactiveRedisIndexedSessionRepository.RedisSession>, DisposableBean,
		InitializingBean {

	private static final Log logger = LogFactory.getLog(ReactiveRedisIndexedSessionRepository.class);

	/**
	 * The default namespace for each key and channel in Redis used by Spring Session.
	 */
	public static final String DEFAULT_NAMESPACE = "spring:session";

	/**
	 * The default Redis database used by Spring Session.
	 */
	public static final int DEFAULT_DATABASE = 0;

	private final ReactiveRedisOperations<String, Object> sessionRedisOperations;

	private final ReactiveRedisTemplate<String, String> keyEventsOperations;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	private BiFunction<String, Map<String, Object>, Mono<MapSession>> redisSessionMapper = new RedisSessionMapperAdapter();

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private ApplicationEventPublisher eventPublisher = (event) -> {
	};

	private String sessionCreatedChannelPrefix;

	private String sessionDeletedChannel;

	private String sessionExpiredChannel;

	private String expiredKeyPrefix;

	private final List<Disposable> subscriptions = new ArrayList<>();

	/**
	 * The namespace for every key used by Spring Session in Redis.
	 */
	private String namespace = DEFAULT_NAMESPACE + ":";

	private int database = DEFAULT_DATABASE;

	private ReactiveRedisSessionIndexer indexer;

	private SortedSetReactiveRedisSessionExpirationStore expirationStore;

	private Duration cleanupInterval = Duration.ofSeconds(60);

	private Clock clock = Clock.systemUTC();

	/**
	 * Creates a new instance with the provided {@link ReactiveRedisOperations}.
	 * @param sessionRedisOperations the {@link ReactiveRedisOperations} to use for
	 * managing the sessions. Cannot be null.
	 * @param keyEventsOperations the {@link ReactiveRedisTemplate} to use to subscribe to
	 * keyspace events. Cannot be null.
	 */
	public ReactiveRedisIndexedSessionRepository(ReactiveRedisOperations<String, Object> sessionRedisOperations,
			ReactiveRedisTemplate<String, String> keyEventsOperations) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations cannot be null");
		Assert.notNull(keyEventsOperations, "keyEventsOperations cannot be null");
		this.sessionRedisOperations = sessionRedisOperations;
		this.keyEventsOperations = keyEventsOperations;
		this.indexer = new ReactiveRedisSessionIndexer(sessionRedisOperations, this.namespace);
		this.expirationStore = new SortedSetReactiveRedisSessionExpirationStore(sessionRedisOperations, this.namespace);
		configureSessionChannels();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		subscribeToRedisEvents();
		setupCleanupTask();
	}

	private void setupCleanupTask() {
		if (!this.cleanupInterval.isZero()) {
			Disposable cleanupExpiredSessionsTask = Flux.interval(this.cleanupInterval, this.cleanupInterval)
				.onBackpressureDrop((count) -> logger
					.debug("Skipping clean-up expired sessions because the previous one is still running."))
				.concatMap((count) -> cleanUpExpiredSessions())
				.subscribe();
			this.subscriptions.add(cleanupExpiredSessionsTask);
		}
	}

	private Flux<Void> cleanUpExpiredSessions() {
		return this.expirationStore.retrieveExpiredSessions(this.clock.instant()).flatMap(this::touch);
	}

	private Mono<Void> touch(String sessionId) {
		return this.sessionRedisOperations.hasKey(getExpiredKey(sessionId)).then();
	}

	@Override
	public void destroy() {
		for (Disposable subscription : this.subscriptions) {
			subscription.dispose();
		}
		this.subscriptions.clear();
	}

	@Override
	public Mono<Map<String, RedisSession>> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		return this.indexer.getSessionIds(indexName, indexValue)
			.flatMap(this::findById)
			.collectMap(RedisSession::getId);
	}

	@Override
	public Mono<RedisSession> createSession() {
		return Mono.fromSupplier(() -> this.sessionIdGenerator.generate())
			.subscribeOn(Schedulers.boundedElastic())
			.publishOn(Schedulers.parallel())
			.map(MapSession::new)
			.doOnNext((session) -> session.setMaxInactiveInterval(this.defaultMaxInactiveInterval))
			.map((session) -> new RedisSession(session, true));
	}

	@Override
	public Mono<Void> save(RedisSession session) {
		// @formatter:off
		return session.save()
				.then(Mono.defer(() -> this.indexer.update(session)))
				.then(Mono.defer(() -> this.expirationStore.add(session.getId(), session.getLastAccessedTime().plus(session.getMaxInactiveInterval()))));
		// @formatter:on
	}

	@Override
	public Mono<RedisSession> findById(String id) {
		return getSession(id, false);
	}

	private Mono<RedisSession> getSession(String sessionId, boolean allowExpired) {
		// @formatter:off
		String sessionKey = getSessionKey(sessionId);
		return this.sessionRedisOperations.opsForHash().entries(sessionKey)
				.collectMap((entry) -> entry.getKey().toString(), Map.Entry::getValue)
				.filter((map) -> !map.isEmpty())
				.flatMap((map) -> this.redisSessionMapper.apply(sessionId, map))
				.filter((session) -> allowExpired || !session.isExpired())
				.map((session) -> new RedisSession(session, false));
		// @formatter:on
	}

	@Override
	public Mono<Void> deleteById(String id) {
		return deleteAndReturn(id).then();
	}

	private Mono<RedisSession> deleteAndReturn(String id) {
		// @formatter:off
		return getSession(id, true)
				.flatMap((session) -> this.sessionRedisOperations.delete(getExpiredKey(session.getId()))
						.thenReturn(session))
				.flatMap((session) -> this.sessionRedisOperations.delete(getSessionKey(session.getId())).thenReturn(session))
				.flatMap((session) -> this.indexer.delete(session.getId()).thenReturn(session))
				.flatMap((session) -> this.expirationStore.remove(session.getId()).thenReturn(session));
		// @formatter:on
	}

	/**
	 * Subscribes to {@code __keyevent@0__:expired} and {@code __keyevent@0__:del} Redis
	 * Keyspaces events and to {@code spring:session:event:0:created:*} Redis Channel
	 * event in order to clean up the sessions and publish the related Spring Session
	 * events.
	 */
	private void subscribeToRedisEvents() {
		Disposable sessionCreatedSubscription = this.sessionRedisOperations
			.listenToPattern(getSessionCreatedChannelPrefix() + "*")
			.flatMap(this::onSessionCreatedChannelMessage)
			.subscribe();
		Disposable sessionDestroyedSubscription = this.keyEventsOperations
			.listenToChannel(getSessionDeletedChannel(), getSessionExpiredChannel())
			.flatMap(this::onKeyDestroyedMessage)
			.subscribe();
		this.subscriptions.addAll(Arrays.asList(sessionCreatedSubscription, sessionDestroyedSubscription));
	}

	@SuppressWarnings("unchecked")
	private Mono<Void> onSessionCreatedChannelMessage(ReactiveSubscription.Message<String, Object> message) {
		return Mono.just(message.getChannel())
			.filter((channel) -> channel.startsWith(getSessionCreatedChannelPrefix()))
			.map((channel) -> {
				int sessionIdBeginIndex = channel.lastIndexOf(":") + 1;
				return channel.substring(sessionIdBeginIndex);
			})
			.flatMap((sessionId) -> {
				Map<String, Object> entries = (Map<String, Object>) message.getMessage();
				return this.redisSessionMapper.apply(sessionId, entries);
			})
			.map((loaded) -> {
				RedisSession session = new RedisSession(loaded, false);
				return new SessionCreatedEvent(this, session);
			})
			.doOnNext(this::publishEvent)
			.then();
	}

	private Mono<Void> onKeyDestroyedMessage(ReactiveSubscription.Message<String, String> message) {
		// @formatter:off
		return Mono.just(message.getMessage())
			.filter((key) -> key.startsWith(getExpiredKeyPrefix())).map((key) -> {
				int sessionIdBeginIndex = key.lastIndexOf(":") + 1;
				return key.substring(sessionIdBeginIndex);
			})
			.flatMap(this::deleteAndReturn)
			.map((session) -> {
				if (message.getChannel().equals(this.sessionDeletedChannel)) {
					return new SessionDeletedEvent(this, session);
				}
				return new SessionExpiredEvent(this, session);
			})
			.doOnNext(this::publishEvent)
			.then();
		// @formatter:on
	}

	private void publishEvent(Object event) {
		this.eventPublisher.publishEvent(event);
	}

	/**
	 * Sets the Redis database index used by Spring Session.
	 * @param database the database index to use
	 */
	public void setDatabase(int database) {
		this.database = database;
		configureSessionChannels();
	}

	/**
	 * Sets the namespace for keys used by Spring Session. Defaults to 'spring:session:'.
	 * @param namespace the namespace to set
	 */
	public void setRedisKeyNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be null or empty");
		this.namespace = namespace.endsWith(":") ? namespace : namespace.trim() + ":";
		this.indexer.setNamespace(this.namespace);
		this.expirationStore.setNamespace(this.namespace);
		configureSessionChannels();
	}

	/**
	 * Sets the interval that the clean-up of expired sessions task should run. Defaults
	 * to 60 seconds. Use {@link Duration#ZERO} to disable it.
	 * @param cleanupInterval the interval to use
	 */
	public void setCleanupInterval(Duration cleanupInterval) {
		Assert.notNull(cleanupInterval, "cleanupInterval cannot be null");
		this.cleanupInterval = cleanupInterval;
	}

	/**
	 * Disables the clean-up task. This is just a shortcut to invoke
	 * {@link #setCleanupInterval(Duration)} passing {@link Duration#ZERO}
	 */
	public void disableCleanupTask() {
		setCleanupInterval(Duration.ZERO);
	}

	/**
	 * Sets the {@link Clock} to use. Defaults to {@link Clock#systemUTC()}.
	 * @param clock the clock to use
	 */
	public void setClock(Clock clock) {
		Assert.notNull(clock, "clock cannot be null");
		this.clock = clock;
	}

	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

	public void setRedisSessionMapper(BiFunction<String, Map<String, Object>, Mono<MapSession>> redisSessionMapper) {
		Assert.notNull(redisSessionMapper, "redisSessionMapper cannot be null");
		this.redisSessionMapper = redisSessionMapper;
	}

	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode cannot be null");
		this.saveMode = saveMode;
	}

	public ReactiveRedisOperations<String, Object> getSessionRedisOperations() {
		return this.sessionRedisOperations;
	}

	public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
		Assert.notNull(eventPublisher, "eventPublisher cannot be null");
		this.eventPublisher = eventPublisher;
	}

	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver cannot be null");
		this.indexer.setIndexResolver(indexResolver);
	}

	private static String getAttributeNameWithPrefix(String attributeName) {
		return RedisSessionMapper.ATTRIBUTE_PREFIX + attributeName;
	}

	private String getSessionKey(String sessionId) {
		return this.namespace + "sessions:" + sessionId;
	}

	private String getExpiredKey(String sessionId) {
		return getExpiredKeyPrefix() + sessionId;
	}

	private String getExpiredKeyPrefix() {
		return this.expiredKeyPrefix;
	}

	private void configureSessionChannels() {
		this.sessionCreatedChannelPrefix = this.namespace + "event:" + this.database + ":created:";
		this.sessionDeletedChannel = "__keyevent@" + this.database + "__:del";
		this.sessionExpiredChannel = "__keyevent@" + this.database + "__:expired";
		this.expiredKeyPrefix = this.namespace + "sessions:expires:";
	}

	public String getSessionCreatedChannel(String sessionId) {
		return getSessionCreatedChannelPrefix() + sessionId;
	}

	public String getSessionCreatedChannelPrefix() {
		return this.sessionCreatedChannelPrefix;
	}

	public String getSessionDeletedChannel() {
		return this.sessionDeletedChannel;
	}

	public String getSessionExpiredChannel() {
		return this.sessionExpiredChannel;
	}

	public final class RedisSession implements Session {

		private final MapSession cached;

		private Map<String, Object> delta = new HashMap<>();

		private boolean isNew;

		private String originalSessionId;

		private Map<String, String> indexes = new HashMap<>();

		public RedisSession(MapSession cached, boolean isNew) {
			this.cached = cached;
			this.isNew = isNew;
			this.originalSessionId = cached.getId();
			if (this.isNew) {
				this.delta.put(RedisSessionMapper.CREATION_TIME_KEY, cached.getCreationTime().toEpochMilli());
				this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY,
						(int) cached.getMaxInactiveInterval().getSeconds());
				this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, cached.getLastAccessedTime().toEpochMilli());
			}
			if (this.isNew || (ReactiveRedisIndexedSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
				getAttributeNames().forEach((attributeName) -> this.delta.put(getAttributeNameWithPrefix(attributeName),
						cached.getAttribute(attributeName)));
			}
		}

		@Override
		public String getId() {
			return this.cached.getId();
		}

		@Override
		public String changeSessionId() {
			String newSessionId = ReactiveRedisIndexedSessionRepository.this.sessionIdGenerator.generate();
			this.cached.setId(newSessionId);
			return newSessionId;
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			T attributeValue = this.cached.getAttribute(attributeName);
			if (attributeValue != null
					&& ReactiveRedisIndexedSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
				this.delta.put(getAttributeNameWithPrefix(attributeName), attributeValue);
			}
			return attributeValue;
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.cached.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.cached.setAttribute(attributeName, attributeValue);
			this.delta.put(getAttributeNameWithPrefix(attributeName), attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.cached.removeAttribute(attributeName);
			this.delta.put(getAttributeNameWithPrefix(attributeName), null);
		}

		@Override
		public Instant getCreationTime() {
			return this.cached.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.cached.setLastAccessedTime(lastAccessedTime);
			this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) getMaxInactiveInterval().getSeconds());
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.cached.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.cached.isExpired();
		}

		public Map<String, String> getIndexes() {
			return Collections.unmodifiableMap(this.indexes);
		}

		private boolean hasChangedSessionId() {
			return !getId().equals(this.originalSessionId);
		}

		private Mono<Void> save() {
			return Mono
				.defer(() -> saveChangeSessionId().then(saveDelta()).doOnSuccess((unused) -> this.isNew = false));
		}

		private Mono<Void> saveDelta() {
			if (this.delta.isEmpty()) {
				return Mono.empty();
			}

			String sessionKey = getSessionKey(getId());
			Mono<Boolean> update = ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations.opsForHash()
				.putAll(sessionKey, new HashMap<>(this.delta));

			String expiredKey = getExpiredKey(getId());
			Mono<Boolean> setTtl;
			Mono<Boolean> updateExpireKey = ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations
				.opsForValue()
				.append(expiredKey, "")
				.hasElement();
			if (getMaxInactiveInterval().getSeconds() >= 0) {
				Duration fiveMinutesFromActualExpiration = getMaxInactiveInterval().plus(Duration.ofMinutes(5));
				setTtl = ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations.expire(sessionKey,
						fiveMinutesFromActualExpiration);
				updateExpireKey = updateExpireKey
					.flatMap((length) -> ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations
						.expire(expiredKey, getMaxInactiveInterval()));
			}
			else {
				setTtl = ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations.persist(sessionKey);
				updateExpireKey = ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations.delete(expiredKey)
					.hasElement();
			}

			Mono<Void> publishCreated = Mono.empty();
			if (this.isNew) {
				String sessionCreatedChannelKey = getSessionCreatedChannel(getId());
				publishCreated = ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations
					.convertAndSend(sessionCreatedChannelKey, this.delta)
					.then();
			}

			return update.flatMap((updated) -> setTtl)
				.then(updateExpireKey)
				.then(publishCreated)
				.then(Mono.fromRunnable(() -> this.delta = new HashMap<>(this.delta.size())))
				.then();
		}

		private Mono<Void> saveChangeSessionId() {
			if (!hasChangedSessionId()) {
				return Mono.empty();
			}

			String sessionId = getId();

			Mono<Void> replaceSessionId = Mono.fromRunnable(() -> this.originalSessionId = sessionId).then();

			if (this.isNew) {
				return Mono.from(replaceSessionId);
			}
			else {
				String originalSessionKey = getSessionKey(this.originalSessionId);
				String sessionKey = getSessionKey(sessionId);
				String originalExpiredKey = getExpiredKey(this.originalSessionId);
				String expiredKey = getExpiredKey(sessionId);

				return renameKey(originalSessionKey, sessionKey)
					.then(Mono.defer(() -> renameKey(originalExpiredKey, expiredKey)))
					.then(Mono.defer(this::replaceSessionIdOnIndexes))
					.then(Mono.defer(() -> replaceSessionId));
			}

		}

		private Mono<Void> replaceSessionIdOnIndexes() {
			return ReactiveRedisIndexedSessionRepository.this.indexer.delete(this.originalSessionId)
				.then(ReactiveRedisIndexedSessionRepository.this.indexer.update(this));
		}

		private Mono<Void> renameKey(String oldKey, String newKey) {
			return ReactiveRedisIndexedSessionRepository.this.sessionRedisOperations.rename(oldKey, newKey)
				.onErrorResume((ex) -> {
					String message = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
					return StringUtils.startsWithIgnoreCase(message, "ERR no such key");
				}, (ex) -> Mono.empty())
				.then();
		}

	}

	private static final class RedisSessionMapperAdapter
			implements BiFunction<String, Map<String, Object>, Mono<MapSession>> {

		private final RedisSessionMapper mapper = new RedisSessionMapper();

		@Override
		public Mono<MapSession> apply(String sessionId, Map<String, Object> map) {
			return Mono.fromSupplier(() -> this.mapper.apply(sessionId, map));
		}

	}

}
