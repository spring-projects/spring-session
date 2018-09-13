/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.Assert;

/**
 * <p>
 * A {@link org.springframework.session.SessionRepository} that is implemented using
 * Spring Data's {@link org.springframework.data.redis.core.RedisOperations}. In a web
 * environment, this is typically used in combination with {@link SessionRepositoryFilter}
 * . This implementation supports {@link SessionDeletedEvent} and
 * {@link SessionExpiredEvent} by implementing {@link MessageListener}.
 * </p>
 *
 * <h2>Creating a new instance</h2>
 *
 * A typical example of how to create a new instance can be seen below:
 *
 * <pre>
 * RedisTemplate&lt;Object, Object&gt; redisTemplate = new RedisTemplate&lt;&gt;();
 *
 * // ... configure redisTemplate ...
 *
 * RedisOperationsSessionRepository redisSessionRepository =
 *         new RedisOperationsSessionRepository(redisTemplate);
 * </pre>
 *
 * <p>
 * For additional information on how to create a RedisTemplate, refer to the
 * <a href = "http://docs.spring.io/spring-data/data-redis/docs/current/reference/html/" >
 * Spring Data Redis Reference</a>.
 * </p>
 *
 * <h2>Storage Details</h2>
 *
 * The sections below outline how Redis is updated for each operation. An example of
 * creating a new session can be found below. The subsequent sections describe the
 * details.
 *
 * <pre>
 * HMSET spring:session:sessions:33fdd1b6-b496-4b33-9f7d-df96679d32fe creationTime 1404360000000 maxInactiveInterval 1800 lastAccessedTime 1404360000000 sessionAttr:attrName someAttrValue sessionAttr2:attrName someAttrValue2
 * EXPIRE spring:session:sessions:33fdd1b6-b496-4b33-9f7d-df96679d32fe 2100
 * APPEND spring:session:sessions:expires:33fdd1b6-b496-4b33-9f7d-df96679d32fe ""
 * EXPIRE spring:session:sessions:expires:33fdd1b6-b496-4b33-9f7d-df96679d32fe 1800
 * SADD spring:session:expirations:1439245080000 expires:33fdd1b6-b496-4b33-9f7d-df96679d32fe
 * EXPIRE spring:session:expirations1439245080000 2100
 * </pre>
 *
 * <h3>Saving a Session</h3>
 *
 * <p>
 * Each session is stored in Redis as a
 * <a href="http://redis.io/topics/data-types#hashes">Hash</a>. Each session is set and
 * updated using the <a href="http://redis.io/commands/hmset">HMSET command</a>. An
 * example of how each session is stored can be seen below.
 * </p>
 *
 * <pre>
 * HMSET spring:session:sessions:33fdd1b6-b496-4b33-9f7d-df96679d32fe creationTime 1404360000000 maxInactiveInterval 1800 lastAccessedTime 1404360000000 sessionAttr:attrName someAttrValue sessionAttr:attrName2 someAttrValue2
 * </pre>
 *
 * <p>
 * In this example, the session following statements are true about the session:
 * </p>
 * <ul>
 * <li>The session id is 33fdd1b6-b496-4b33-9f7d-df96679d32fe</li>
 * <li>The session was created at 1404360000000 in milliseconds since midnight of 1/1/1970
 * GMT.</li>
 * <li>The session expires in 1800 seconds (30 minutes).</li>
 * <li>The session was last accessed at 1404360000000 in milliseconds since midnight of
 * 1/1/1970 GMT.</li>
 * <li>The session has two attributes. The first is "attrName" with the value of
 * "someAttrValue". The second session attribute is named "attrName2" with the value of
 * "someAttrValue2".</li>
 * </ul>
 *
 *
 * <h3>Optimized Writes</h3>
 *
 * <p>
 * The {@link org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession} keeps track of the properties that have changed and only
 * updates those. This means if an attribute is written once and read many times we only
 * need to write that attribute once. For example, assume the session attribute
 * "sessionAttr2" from earlier was updated. The following would be executed upon saving:
 * </p>
 *
 * <pre>
 * HMSET spring:session:sessions:33fdd1b6-b496-4b33-9f7d-df96679d32fe sessionAttr:attrName2 newValue
 * </pre>
 *
 * <h3>SessionCreatedEvent</h3>
 *
 * <p>
 * When a session is created an event is sent to Redis with the channel of
 * "spring:session:channel:created:33fdd1b6-b496-4b33-9f7d-df96679d32fe" such that
 * "33fdd1b6-b496-4b33-9f7d-df96679d32fe" is the sesion id. The body of the event will be
 * the session that was created.
 * </p>
 *
 * <p>
 * If registered as a {@link MessageListener}, then
 * {@link RedisOperationsSessionRepository} will then translate the Redis message into a
 * {@link SessionCreatedEvent}.
 * </p>
 *
 * <h3>Expiration</h3>
 *
 * <p>
 * An expiration is associated to each session using the
 * <a href="http://redis.io/commands/expire">EXPIRE command</a> based upon the
 * {@link org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession#getMaxInactiveInterval()}
 * . For example:
 * </p>
 *
 * <pre>
 * EXPIRE spring:session:sessions:33fdd1b6-b496-4b33-9f7d-df96679d32fe 2100
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
 * <b>NOTE:</b> The {@link #findById(String)} method ensures that no expired sessions
 * will be returned. This means there is no need to check the expiration before using a
 * session
 * </p>
 *
 * <p>
 * Spring Session relies on the expired and delete
 * <a href="http://redis.io/topics/notifications">keyspace notifications</a> from Redis to
 * fire a SessionDestroyedEvent. It is the SessionDestroyedEvent that ensures resources
 * associated with the Session are cleaned up. For example, when using Spring Session's
 * WebSocket support the Redis expired or delete event is what triggers any WebSocket
 * connections associated with the session to be closed.
 * </p>
 *
 * <p>
 * Expiration is not tracked directly on the session key itself since this would mean the
 * session data would no longer be available. Instead a special session expires key is
 * used. In our example the expires key is:
 * </p>
 *
 * <pre>
 * APPEND spring:session:sessions:expires:33fdd1b6-b496-4b33-9f7d-df96679d32fe ""
 * EXPIRE spring:session:sessions:expires:33fdd1b6-b496-4b33-9f7d-df96679d32fe 1800
 * </pre>
 *
 * <p>
 * When a session expires key is deleted or expires, the keyspace notification triggers a
 * lookup of the actual session and a {@link SessionDestroyedEvent} is fired.
 * </p>
 *
 * <p>
 * One problem with relying on Redis expiration exclusively is that Redis makes no
 * guarantee of when the expired event will be fired if they key has not been accessed.
 * Specifically the background task that Redis uses to clean up expired keys is a low
 * priority task and may not trigger the key expiration. For additional details see
 * <a href="http://redis.io/topics/notifications">Timing of expired events</a> section in
 * the Redis documentation.
 * </p>
 *
 * <p>
 * To circumvent the fact that expired events are not guaranteed to happen we can ensure
 * that each key is accessed when it is expected to expire. This means that if the TTL is
 * expired on the key, Redis will remove the key and fire the expired event when we try to
 * access they key.
 * </p>
 *
 * <p>
 * For this reason, each session expiration is also tracked to the nearest minute. This
 * allows a background task to access the potentially expired sessions to ensure that
 * Redis expired events are fired in a more deterministic fashion. For example:
 * </p>
 *
 * <pre>
 * SADD spring:session:expirations:1439245080000 expires:33fdd1b6-b496-4b33-9f7d-df96679d32fe
 * EXPIRE spring:session:expirations1439245080000 2100
 * </pre>
 *
 * <p>
 * The background task will then use these mappings to explicitly request each session
 * expires key. By accessing the key, rather than deleting it, we ensure that Redis
 * deletes the key for us only if the TTL is expired.
 * </p>
 * <p>
 * <b>NOTE</b>: We do not explicitly delete the keys since in some instances there may be
 * a race condition that incorrectly identifies a key as expired when it is not. Short of
 * using distributed locks (which would kill our performance) there is no way to ensure
 * the consistency of the expiration mapping. By simply accessing the key, we ensure that
 * the key is only removed if the TTL on that key is expired.
 * </p>
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.0
 */
public class RedisOperationsSessionRepository implements
		FindByIndexNameSessionRepository<RedisOperationsSessionRepository.RedisSession>,
		MessageListener {
	private static final Log logger = LogFactory
			.getLog(RedisOperationsSessionRepository.class);

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	static PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	/**
	 * The default Redis database used by Spring Session.
	 */
	public static final int DEFAULT_DATABASE = 0;

	/**
	 * The default namespace for each key and channel in Redis used by Spring Session.
	 */
	public static final String DEFAULT_NAMESPACE = "spring:session";

	/**
	 * The key in the Hash representing
	 * {@link org.springframework.session.Session#getCreationTime()}.
	 */
	static final String CREATION_TIME_ATTR = "creationTime";

	/**
	 * The key in the Hash representing
	 * {@link org.springframework.session.Session#getMaxInactiveInterval()}
	 * .
	 */
	static final String MAX_INACTIVE_ATTR = "maxInactiveInterval";

	/**
	 * The key in the Hash representing
	 * {@link org.springframework.session.Session#getLastAccessedTime()}.
	 */
	static final String LAST_ACCESSED_ATTR = "lastAccessedTime";

	/**
	 * The prefix of the key used for session attributes. The suffix is the name of
	 * the session attribute. For example, if the session contained an attribute named
	 * attributeName, then there would be an entry in the hash named
	 * sessionAttr:attributeName that mapped to its value.
	 */
	static final String SESSION_ATTR_PREFIX = "sessionAttr:";

	private int database = RedisOperationsSessionRepository.DEFAULT_DATABASE;

	/**
	 * The namespace for every key used by Spring Session in Redis.
	 */
	private String namespace = DEFAULT_NAMESPACE + ":";

	private String sessionCreatedChannelPrefix;

	private String sessionDeletedChannel;

	private String sessionExpiredChannel;

	private final RedisOperations<Object, Object> sessionRedisOperations;

	private final RedisSessionExpirationPolicy expirationPolicy;

	private ApplicationEventPublisher eventPublisher = new ApplicationEventPublisher() {
		@Override
		public void publishEvent(ApplicationEvent event) {
		}

		@Override
		public void publishEvent(Object event) {
		}
	};

	/**
	 * If non-null, this value is used to override the default value for
	 * {@link RedisSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private RedisSerializer<Object> defaultSerializer = new JdkSerializationRedisSerializer();

	private RedisFlushMode redisFlushMode = RedisFlushMode.ON_SAVE;

	/**
	 * Creates a new instance. For an example, refer to the class level javadoc.
	 *
	 * @param sessionRedisOperations the {@link RedisOperations} to use for managing the
	 * sessions. Cannot be null.
	 */
	public RedisOperationsSessionRepository(
			RedisOperations<Object, Object> sessionRedisOperations) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations cannot be null");
		this.sessionRedisOperations = sessionRedisOperations;
		this.expirationPolicy = new RedisSessionExpirationPolicy(sessionRedisOperations,
				this::getExpirationsKey, this::getSessionKey);
		configureSessionChannels();
	}

	/**
	 * Sets the {@link ApplicationEventPublisher} that is used to publish
	 * {@link SessionDestroyedEvent}. The default is to not publish a
	 * {@link SessionDestroyedEvent}.
	 *
	 * @param applicationEventPublisher the {@link ApplicationEventPublisher} that is used
	 * to publish {@link SessionDestroyedEvent}. Cannot be null.
	 */
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher,
				"applicationEventPublisher cannot be null");
		this.eventPublisher = applicationEventPublisher;
	}

	/**
	 * Sets the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * timeout. The default is 1800 (30 minutes).
	 *
	 * @param defaultMaxInactiveInterval the number of seconds that the {@link Session}
	 * should be kept alive between client requests.
	 */
	public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Sets the default redis serializer. Replaces default serializer which is based on
	 * {@link JdkSerializationRedisSerializer}.
	 *
	 * @param defaultSerializer the new default redis serializer
	 */
	public void setDefaultSerializer(RedisSerializer<Object> defaultSerializer) {
		Assert.notNull(defaultSerializer, "defaultSerializer cannot be null");
		this.defaultSerializer = defaultSerializer;
	}

	/**
	 * Sets the redis flush mode. Default flush mode is {@link RedisFlushMode#ON_SAVE}.
	 *
	 * @param redisFlushMode the new redis flush mode
	 */
	public void setRedisFlushMode(RedisFlushMode redisFlushMode) {
		Assert.notNull(redisFlushMode, "redisFlushMode cannot be null");
		this.redisFlushMode = redisFlushMode;
	}

	/**
	 * Sets the database index to use. Defaults to {@link #DEFAULT_DATABASE}.
	 * @param database the database index to use
	 */
	public void setDatabase(int database) {
		this.database = database;
		configureSessionChannels();
	}

	private void configureSessionChannels() {
		this.sessionCreatedChannelPrefix = this.namespace + "event:" + this.database
				+ ":created:";
		this.sessionDeletedChannel = "__keyevent@" + this.database + "__:del";
		this.sessionExpiredChannel = "__keyevent@" + this.database + "__:expired";
	}

	/**
	 * Returns the {@link RedisOperations} used for sessions.
	 * @return the {@link RedisOperations} used for sessions
	 * @since 2.0.0
	 */
	public RedisOperations<Object, Object> getSessionRedisOperations() {
		return this.sessionRedisOperations;
	}

	@Override
	public void save(RedisSession session) {
		session.saveDelta();
		if (session.isNew()) {
			String sessionCreatedKey = getSessionCreatedChannel(session.getId());
			this.sessionRedisOperations.convertAndSend(sessionCreatedKey, session.delta);
			session.setNew(false);
		}
	}

	public void cleanupExpiredSessions() {
		this.expirationPolicy.cleanExpiredSessions();
	}

	@Override
	public RedisSession findById(String id) {
		return getSession(id, false);
	}

	@Override
	public Map<String, RedisSession> findByIndexNameAndIndexValue(String indexName,
			String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		String principalKey = getPrincipalKey(indexValue);
		Set<Object> sessionIds = this.sessionRedisOperations.boundSetOps(principalKey)
				.members();
		Map<String, RedisSession> sessions = new HashMap<>(
				sessionIds.size());
		for (Object id : sessionIds) {
			RedisSession session = findById((String) id);
			if (session != null) {
				sessions.put(session.getId(), session);
			}
		}
		return sessions;
	}

	/**
	 * Gets the session.
	 * @param id the session id
	 * @param allowExpired if true, will also include expired sessions that have not been
	 * deleted. If false, will ensure expired sessions are not returned.
	 * @return the Redis session
	 */
	private RedisSession getSession(String id, boolean allowExpired) {
		Map<Object, Object> entries = getSessionBoundHashOperations(id).entries();
		if (entries.isEmpty()) {
			return null;
		}
		MapSession loaded = loadSession(id, entries);
		if (!allowExpired && loaded.isExpired()) {
			return null;
		}
		RedisSession result = new RedisSession(loaded);
		result.originalLastAccessTime = loaded.getLastAccessedTime();
		return result;
	}

	private MapSession loadSession(String id, Map<Object, Object> entries) {
		MapSession loaded = new MapSession(id);
		for (Map.Entry<Object, Object> entry : entries.entrySet()) {
			String key = (String) entry.getKey();
			if (CREATION_TIME_ATTR.equals(key)) {
				loaded.setCreationTime(Instant.ofEpochMilli((long) entry.getValue()));
			}
			else if (MAX_INACTIVE_ATTR.equals(key)) {
				loaded.setMaxInactiveInterval(Duration.ofSeconds((int) entry.getValue()));
			}
			else if (LAST_ACCESSED_ATTR.equals(key)) {
				loaded.setLastAccessedTime(Instant.ofEpochMilli((long) entry.getValue()));
			}
			else if (key.startsWith(SESSION_ATTR_PREFIX)) {
				loaded.setAttribute(key.substring(SESSION_ATTR_PREFIX.length()),
						entry.getValue());
			}
		}
		return loaded;
	}

	@Override
	public void deleteById(String sessionId) {
		RedisSession session = getSession(sessionId, true);
		if (session == null) {
			return;
		}

		cleanupPrincipalIndex(session);
		this.expirationPolicy.onDelete(session);

		String expireKey = getExpiredKey(session.getId());
		this.sessionRedisOperations.delete(expireKey);

		session.setMaxInactiveInterval(Duration.ZERO);
		save(session);
	}

	@Override
	public RedisSession createSession() {
		RedisSession redisSession = new RedisSession();
		if (this.defaultMaxInactiveInterval != null) {
			redisSession.setMaxInactiveInterval(
					Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		return redisSession;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onMessage(Message message, byte[] pattern) {
		byte[] messageChannel = message.getChannel();
		byte[] messageBody = message.getBody();

		String channel = new String(messageChannel);

		if (channel.startsWith(this.sessionCreatedChannelPrefix)) {
			// TODO: is this thread safe?
			Map<Object, Object> loaded = (Map<Object, Object>) this.defaultSerializer
					.deserialize(message.getBody());
			handleCreated(loaded, channel);
			return;
		}

		String body = new String(messageBody);
		if (!body.startsWith(getExpiredKeyPrefix())) {
			return;
		}

		boolean isDeleted = channel.equals(this.sessionDeletedChannel);
		if (isDeleted || channel.equals(this.sessionExpiredChannel)) {
			int beginIndex = body.lastIndexOf(":") + 1;
			int endIndex = body.length();
			String sessionId = body.substring(beginIndex, endIndex);

			RedisSession session = getSession(sessionId, true);

			if (session == null) {
				logger.warn("Unable to publish SessionDestroyedEvent for session "
						+ sessionId);
				return;
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Publishing SessionDestroyedEvent for session " + sessionId);
			}

			cleanupPrincipalIndex(session);

			if (isDeleted) {
				handleDeleted(session);
			}
			else {
				handleExpired(session);
			}
		}
	}

	private void cleanupPrincipalIndex(RedisSession session) {
		String sessionId = session.getId();
		String principal = PRINCIPAL_NAME_RESOLVER.resolvePrincipal(session);
		if (principal != null) {
			this.sessionRedisOperations.boundSetOps(getPrincipalKey(principal))
					.remove(sessionId);
		}
	}

	private void handleCreated(Map<Object, Object> loaded, String channel) {
		String id = channel.substring(channel.lastIndexOf(":") + 1);
		Session session = loadSession(id, loaded);
		publishEvent(new SessionCreatedEvent(this, session));
	}

	private void handleDeleted(RedisSession session) {
		publishEvent(new SessionDeletedEvent(this, session));
	}

	private void handleExpired(RedisSession session) {
		publishEvent(new SessionExpiredEvent(this, session));
	}

	private void publishEvent(ApplicationEvent event) {
		try {
			this.eventPublisher.publishEvent(event);
		}
		catch (Throwable ex) {
			logger.error("Error publishing " + event + ".", ex);
		}
	}

	public void setRedisKeyNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be null or empty");
		this.namespace = namespace.trim() + ":";
		configureSessionChannels();
	}

	/**
	 * Gets the Hash key for this session by prefixing it appropriately.
	 *
	 * @param sessionId the session id
	 * @return the Hash key for this session by prefixing it appropriately.
	 */
	String getSessionKey(String sessionId) {
		return this.namespace + "sessions:" + sessionId;
	}

	String getPrincipalKey(String principalName) {
		return this.namespace + "index:"
				+ FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":"
				+ principalName;
	}

	String getExpirationsKey(long expiration) {
		return this.namespace + "expirations:" + expiration;
	}

	private String getExpiredKey(String sessionId) {
		return getExpiredKeyPrefix() + sessionId;
	}

	private String getSessionCreatedChannel(String sessionId) {
		return getSessionCreatedChannelPrefix() + sessionId;
	}

	private String getExpiredKeyPrefix() {
		return this.namespace + "sessions:expires:";
	}

	/**
	 * Gets the prefix for the channel that {@link SessionCreatedEvent}s are published to.
	 * The suffix is the session id of the session that was created.
	 * @return the prefix for the channel that {@link SessionCreatedEvent}s are published
	 * to
	 */
	public String getSessionCreatedChannelPrefix() {
		return this.sessionCreatedChannelPrefix;
	}

	/**
	 * Gets the name of the channel that {@link SessionDeletedEvent}s are published to.
	 * @return the name for the channel that {@link SessionDeletedEvent}s are published to
	 */
	public String getSessionDeletedChannel() {
		return this.sessionDeletedChannel;
	}

	/**
	 * Gets the name of the channel that {@link SessionExpiredEvent}s are published to.
	 * @return the name for the channel that {@link SessionExpiredEvent}s are published to
	 */
	public String getSessionExpiredChannel() {
		return this.sessionExpiredChannel;
	}

	/**
	 * Gets the {@link BoundHashOperations} to operate on a {@link Session}.
	 * @param sessionId the id of the {@link Session} to work with
	 * @return the {@link BoundHashOperations} to operate on a {@link Session}
	 */
	private BoundHashOperations<Object, Object, Object> getSessionBoundHashOperations(
			String sessionId) {
		String key = getSessionKey(sessionId);
		return this.sessionRedisOperations.boundHashOps(key);
	}

	/**
	 * Gets the key for the specified session attribute.
	 *
	 * @param attributeName the attribute name
	 * @return the attribute key name
	 */
	static String getSessionAttrNameKey(String attributeName) {
		return SESSION_ATTR_PREFIX + attributeName;
	}

	/**
	 * A custom implementation of {@link Session} that uses a {@link MapSession} as the
	 * basis for its mapping. It keeps track of any attributes that have changed. When
	 * {@link org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession#saveDelta()}
	 * is invoked all the attributes that have been changed will be persisted.
	 *
	 * @author Rob Winch
	 * @since 1.0
	 */
	final class RedisSession implements Session {
		private final MapSession cached;
		private Instant originalLastAccessTime;
		private Map<String, Object> delta = new HashMap<>();
		private boolean isNew;
		private String originalPrincipalName;
		private String originalSessionId;

		/**
		 * Creates a new instance ensuring to mark all of the new attributes to be
		 * persisted in the next save operation.
		 */
		RedisSession() {
			this(new MapSession());
			this.delta.put(CREATION_TIME_ATTR, getCreationTime().toEpochMilli());
			this.delta.put(MAX_INACTIVE_ATTR, (int) getMaxInactiveInterval().getSeconds());
			this.delta.put(LAST_ACCESSED_ATTR, getLastAccessedTime().toEpochMilli());
			this.isNew = true;
			this.flushImmediateIfNecessary();
		}

		/**
		 * Creates a new instance from the provided {@link MapSession}.
		 *
		 * @param cached the {@link MapSession} that represents the persisted session that
		 * was retrieved. Cannot be null.
		 */
		RedisSession(MapSession cached) {
			Assert.notNull(cached, "MapSession cannot be null");
			this.cached = cached;
			this.originalPrincipalName = PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this);
			this.originalSessionId = cached.getId();
		}

		public void setNew(boolean isNew) {
			this.isNew = isNew;
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.cached.setLastAccessedTime(lastAccessedTime);
			this.putAndFlush(LAST_ACCESSED_ATTR, getLastAccessedTime().toEpochMilli());
		}

		@Override
		public boolean isExpired() {
			return this.cached.isExpired();
		}

		public boolean isNew() {
			return this.isNew;
		}

		@Override
		public Instant getCreationTime() {
			return this.cached.getCreationTime();
		}

		@Override
		public String getId() {
			return this.cached.getId();
		}

		@Override
		public String changeSessionId() {
			return this.cached.changeSessionId();
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			this.putAndFlush(MAX_INACTIVE_ATTR, (int) getMaxInactiveInterval().getSeconds());
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.cached.getMaxInactiveInterval();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			return this.cached.getAttribute(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.cached.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.cached.setAttribute(attributeName, attributeValue);
			this.putAndFlush(getSessionAttrNameKey(attributeName), attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.cached.removeAttribute(attributeName);
			this.putAndFlush(getSessionAttrNameKey(attributeName), null);
		}

		private void flushImmediateIfNecessary() {
			if (RedisOperationsSessionRepository.this.redisFlushMode == RedisFlushMode.IMMEDIATE) {
				saveDelta();
			}
		}

		private void putAndFlush(String a, Object v) {
			this.delta.put(a, v);
			this.flushImmediateIfNecessary();
		}

		/**
		 * Saves any attributes that have been changed and updates the expiration of this
		 * session.
		 */
		private void saveDelta() {
			String sessionId = getId();
			saveChangeSessionId(sessionId);
			if (this.delta.isEmpty()) {
				return;
			}
			getSessionBoundHashOperations(sessionId).putAll(this.delta);
			String principalSessionKey = getSessionAttrNameKey(
					FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
			String securityPrincipalSessionKey = getSessionAttrNameKey(
					SPRING_SECURITY_CONTEXT);
			if (this.delta.containsKey(principalSessionKey)
					|| this.delta.containsKey(securityPrincipalSessionKey)) {
				if (this.originalPrincipalName != null) {
					String originalPrincipalRedisKey = getPrincipalKey(
							this.originalPrincipalName);
					RedisOperationsSessionRepository.this.sessionRedisOperations
							.boundSetOps(originalPrincipalRedisKey).remove(sessionId);
				}
				String principal = PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this);
				this.originalPrincipalName = principal;
				if (principal != null) {
					String principalRedisKey = getPrincipalKey(principal);
					RedisOperationsSessionRepository.this.sessionRedisOperations
							.boundSetOps(principalRedisKey).add(sessionId);
				}
			}

			this.delta = new HashMap<>(this.delta.size());

			Long originalExpiration = (this.originalLastAccessTime != null)
					? this.originalLastAccessTime.plus(getMaxInactiveInterval())
							.toEpochMilli()
					: null;
			RedisOperationsSessionRepository.this.expirationPolicy
					.onExpirationUpdated(originalExpiration, this);
		}

		private void saveChangeSessionId(String sessionId) {
			if (!sessionId.equals(this.originalSessionId)) {
				if (!isNew()) {
					String originalSessionIdKey = getSessionKey(this.originalSessionId);
					String sessionIdKey = getSessionKey(sessionId);
					RedisOperationsSessionRepository.this.sessionRedisOperations.rename(
							originalSessionIdKey, sessionIdKey);
					String originalExpiredKey = getExpiredKey(this.originalSessionId);
					String expiredKey = getExpiredKey(sessionId);
					try {
						RedisOperationsSessionRepository.this.sessionRedisOperations.rename(
								originalExpiredKey, expiredKey);
					}
					catch (NonTransientDataAccessException ex) {
						if (!"ERR no such key".equals(NestedExceptionUtils
								.getMostSpecificCause(ex).getMessage())) {
							throw ex;
						}
					}
				}
				this.originalSessionId = sessionId;
			}
		}
	}

	/**
	 * Principal name resolver helper class.
	 */
	static class PrincipalNameResolver {
		private SpelExpressionParser parser = new SpelExpressionParser();

		public String resolvePrincipal(Session session) {
			String principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
			if (principalName != null) {
				return principalName;
			}
			Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (authentication != null) {
				Expression expression = this.parser
						.parseExpression("authentication?.name");
				return expression.getValue(authentication, String.class);
			}
			return null;
		}

	}
}
