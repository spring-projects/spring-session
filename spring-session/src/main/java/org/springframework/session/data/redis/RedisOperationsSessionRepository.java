/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.session.data.redis;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.Assert;

/**
 * <p>
 * A {@link org.springframework.session.SessionRepository} that is implemented
 * using Spring Data's
 * {@link org.springframework.data.redis.core.RedisOperations}. In a web
 * environment, this is typically used in combination with
 * {@link SessionRepositoryFilter}.
 * </p>
 *
 * <h2>Creating a new instance</h2>
 *
 * A typical example of how to create a new instance can be seen below:
 *
 * <pre>
 * JedisConnectionFactory factory = new JedisConnectionFactory();
 *
 * RedisOperationsSessionRepository redisSessionRepository = new RedisOperationsSessionRepository(
 * 		factory);
 * </pre>
 *
 * <p>
 * For additional information on how to create a RedisTemplate, refer to the <a
 * href =
 * "http://docs.spring.io/spring-data/data-redis/docs/current/reference/html/"
 * >Spring Data Redis Reference</a>.
 * </p>
 *
 * <h2>Storage Details</h2>
 *
 * <p>
 * Each session is stored in Redis as a <a
 * href="http://redis.io/topics/data-types#hashes">Hash</a>. Each session is set
 * and updated using the <a href="http://redis.io/commands/hmset">HMSET
 * command</a>. An example of how each session is stored can be seen below.
 * </p>
 *
 * <pre>
 *     HMSET spring:session:sessions:<session-id> creationTime 1404360000000 maxInactiveInterval 1800 lastAccessedTime 1404360000000 sessionAttr:<attrName> someAttrValue sessionAttr2:<attrName> someAttrValue2
 * </pre>
 *
 * <p>
 * An expiration is associated to each session using the <a
 * href="http://redis.io/commands/expire">EXPIRE command</a> based upon the
 * {@link org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession#getMaxInactiveInterval()}
 * . For example:
 * </p>
 *
 * <pre>
 *    EXPIRE spring:session:sessions:<session-id> 1800
 * </pre>
 *
 * <p>
 * The {@link RedisSession} keeps track of the properties that have changed and
 * only updates those. This means if an attribute is written once and read many
 * times we only need to write that attribute once. For example, assume the
 * session attribute "sessionAttr2" from earlier was updated. The following
 * would be executed upon saving:
 * </p>
 *
 * <pre>
 *     HMSET spring:session:sessions:<session-id> sessionAttr2:<attrName> newValue
 *     EXPIRE spring:session:sessions:<session-id> 1800
 * </pre>
 *
 * Each session expiration is also tracked to the nearest minute. This allows a
 * background task to cleanup expired sessions in a deterministic fashion. For
 * example:
 *
 * <pre>
 *     SADD spring:session:expirations:<expire-rounded-up-to-nearest-minute> <session-id>
 *     EXPIRE spring:session:expirations:<expire-rounded-up-to-nearest-minute> 1800
 * </pre>
 *
 * The Redis expiration is still placed on each key to ensure that if the server
 * is down when the session expires, it is still cleaned up.
 *
 * @since 1.0
 *
 * @author Rob Winch
 */
public class RedisOperationsSessionRepository implements SessionRepository<RedisOperationsSessionRepository.RedisSession> {
    /**
     * The prefix for each key of the Redis Hash representing a single session. The suffix is the unique session id.
     */
    static final String BOUNDED_HASH_KEY_PREFIX = "spring:session:sessions:";

    /**
     * The key in the Hash representing {@link org.springframework.session.ExpiringSession#getCreationTime()}
     */
    static final String CREATION_TIME_ATTR = "creationTime";

    /**
     * The key in the Hash representing {@link org.springframework.session.ExpiringSession#getMaxInactiveInterval()}
     */
    static final String MAX_INACTIVE_ATTR = "maxInactiveInterval";

    /**
     * The key in the Hash representing {@link org.springframework.session.ExpiringSession#getLastAccessedTime()}
     */
    static final String LAST_ACCESSED_ATTR = "lastAccessedTime";

    /**
     * The prefix of the key for used for session attributes. The suffix is the name of the session attribute. For
     * example, if the session contained an attribute named attributeName, then there would be an entry in the hash named
     * sessionAttr:attributeName that mapped to its value.
     */
    static final String SESSION_ATTR_PREFIX = "sessionAttr:";

    private final RedisOperations<String,ExpiringSession> sessionRedisOperations;

    private final RedisSessionExpirationPolicy expirationPolicy;

    /**
     * If non-null, this value is used to override {@link RedisSession#setDefaultMaxInactiveInterval(int)}.
     */
    private Integer defaultMaxInactiveInterval;

    /**
     * Allows creating an instance and uses a default {@link RedisOperations} for both managing the session and the expirations.
     *
     * @param redisConnectionFactory the {@link RedisConnectionFactory} to use.
     */
    @SuppressWarnings("unchecked")
    public RedisOperationsSessionRepository(RedisConnectionFactory redisConnectionFactory) {
        this(createDefaultTemplate(redisConnectionFactory), createDefaultTemplate(redisConnectionFactory));
    }

    /**
     * Creates a new instance. For an example, refer to the class level javadoc.
     *
     * @param sessionRedisOperations The {@link RedisOperations} to use for managing the sessions. Cannot be null.
     * @param expirationRedisOperations The {@link RedisOperations} to use for managing expiration mappings. Cannot be null.
     */
    public RedisOperationsSessionRepository(RedisOperations<String, ExpiringSession> sessionRedisOperations, RedisOperations<String,String> expirationRedisOperations) {
        Assert.notNull(sessionRedisOperations, "sessionRedisOperations cannot be null");
        Assert.notNull(expirationRedisOperations, "expirationRedisOperations cannot be null");
        this.sessionRedisOperations = sessionRedisOperations;
        this.expirationPolicy = new RedisSessionExpirationPolicy(sessionRedisOperations, expirationRedisOperations);
    }

    /**
     * Sets the maximum inactive interval in seconds between requests before newly created sessions will be
     * invalidated. A negative time indicates that the session will never timeout. The default is 1800 (30 minutes).
     *
     *  @param defaultMaxInactiveInterval the number of seconds that the {@link Session} should be kept alive between
     *                                    client requests.
     */
    public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    @Override
    public void save(RedisSession session) {
        session.saveDelta();
    }

    @Scheduled(cron="0 * * * * *")
    public void cleanupExpiredSessions() {
        this.expirationPolicy.cleanExpiredSessions();
    }

    @Override
    public RedisSession getSession(String id) {
        return getSession(id, false);
    }

    /**
     *
     * @param id the session id
     * @param allowExpired
     *            if true, will also include expired sessions that have not been
     *            deleted. If false, will ensure expired sessions are not
     *            returned.
     * @return
     */
    private RedisSession getSession(String id, boolean allowExpired) {
        Map<Object, Object> entries = getSessionBoundHashOperations(id).entries();
        if(entries.isEmpty()) {
            return null;
        }
        MapSession loaded = new MapSession();
        loaded.setId(id);
        for(Map.Entry<Object,Object> entry : entries.entrySet()) {
            String key = (String) entry.getKey();
            if(CREATION_TIME_ATTR.equals(key)) {
                loaded.setCreationTime((Long) entry.getValue());
            } else if(MAX_INACTIVE_ATTR.equals(key)) {
                loaded.setMaxInactiveInterval((Integer) entry.getValue());
            } else if(LAST_ACCESSED_ATTR.equals(key)) {
                loaded.setLastAccessedTime((Long) entry.getValue());
            } else if(key.startsWith(SESSION_ATTR_PREFIX)) {
                loaded.setAttribute(key.substring(SESSION_ATTR_PREFIX.length()), entry.getValue());
            }
        }
        if(!allowExpired && loaded.isExpired()) {
            return null;
        }
        RedisSession result = new RedisSession(loaded);
        result.originalLastAccessTime = loaded.getLastAccessedTime() + TimeUnit.SECONDS.toMillis(loaded.getMaxInactiveInterval());
        result.setLastAccessedTime(System.currentTimeMillis());
        return result;
    }

    @Override
    public void delete(String sessionId) {
        ExpiringSession session = getSession(sessionId, true);
        if(session == null) {
            return;
        }

        String key = getKey(sessionId);
        expirationPolicy.onDelete(session);

        // always delete they key since session may be null if just expired
        this.sessionRedisOperations.delete(key);
    }

    @Override
    public RedisSession createSession() {
        RedisSession redisSession = new RedisSession();
        if(defaultMaxInactiveInterval != null) {
            redisSession.setMaxInactiveInterval(defaultMaxInactiveInterval);
        }
        return redisSession;
    }

    /**
     * Gets the Hash key for this session by prefixing it appropriately.
     *
     * @param sessionId the session id
     * @return the Hash key for this session by prefixing it appropriately.
     */
    static String getKey(String sessionId) {
        return BOUNDED_HASH_KEY_PREFIX + sessionId;
    }

    /**
     * Gets the key for the specified session attribute
     *
     * @param attributeName
     * @return
     */
    static String getSessionAttrNameKey(String attributeName) {
        return SESSION_ATTR_PREFIX + attributeName;
    }

    /**
     * Gets the {@link BoundHashOperations} to operate on a {@link Session}
     * @param sessionId the id of the {@link Session} to work with
     * @return the {@link BoundHashOperations} to operate on a {@link Session}
     */
    private BoundHashOperations<String, Object, Object> getSessionBoundHashOperations(String sessionId) {
        String key = getKey(sessionId);
        return this.sessionRedisOperations.boundHashOps(key);
    }

    @SuppressWarnings("rawtypes")
    private static RedisTemplate createDefaultTemplate(RedisConnectionFactory connectionFactory) {
        Assert.notNull(connectionFactory,"connectionFactory cannot be null");
        RedisTemplate<String, ExpiringSession> template = new RedisTemplate<String, ExpiringSession>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    /**
     * A custom implementation of {@link Session} that uses a {@link MapSession} as the basis for its mapping. It keeps
     * track of any attributes that have changed. When
     * {@link org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession#saveDelta()} is invoked
     * all the attributes that have been changed will be persisted.
     *
     * @since 1.0
     * @author Rob Winch
     */
    final class RedisSession implements ExpiringSession {
        private final MapSession cached;
        private Long originalLastAccessTime;
        private Map<String, Object> delta = new HashMap<String,Object>();

        /**
         * Creates a new instance ensuring to mark all of the new attributes to be persisted in the next save operation.
         */
        RedisSession() {
            this(new MapSession());
            delta.put(CREATION_TIME_ATTR, getCreationTime());
            delta.put(MAX_INACTIVE_ATTR, getMaxInactiveInterval());
            delta.put(LAST_ACCESSED_ATTR, getLastAccessedTime());
        }

        /**
         * Creates a new instance from the provided {@link MapSession}
         *
         * @param cached the {@MapSession} that represents the persisted session that was retrieved. Cannot be null.
         */
        RedisSession(MapSession cached) {
            Assert.notNull("MapSession cannot be null");
            this.cached = cached;
        }

        public void setLastAccessedTime(long lastAccessedTime) {
            cached.setLastAccessedTime(lastAccessedTime);
            delta.put(LAST_ACCESSED_ATTR, getLastAccessedTime());
        }

        @Override
        public boolean isExpired() {
            return cached.isExpired();
        }

        @Override
        public long getCreationTime() {
            return cached.getCreationTime();
        }

        @Override
        public String getId() {
            return cached.getId();
        }

        @Override
        public long getLastAccessedTime() {
            return cached.getLastAccessedTime();
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            cached.setMaxInactiveInterval(interval);
            delta.put(MAX_INACTIVE_ATTR, getMaxInactiveInterval());
        }

        @Override
        public int getMaxInactiveInterval() {
            return cached.getMaxInactiveInterval();
        }

        @Override
        public Object getAttribute(String attributeName) {
            return cached.getAttribute(attributeName);
        }

        @Override
        public Set<String> getAttributeNames() {
            return cached.getAttributeNames();
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            cached.setAttribute(attributeName, attributeValue);
            delta.put(getSessionAttrNameKey(attributeName), attributeValue);
        }

        @Override
        public void removeAttribute(String attributeName) {
            cached.removeAttribute(attributeName);
            delta.put(getSessionAttrNameKey(attributeName), null);
        }

        /**
         * Saves any attributes that have been changed and updates the expiration of this session.
         */
        private void saveDelta() {
            String sessionId = getId();
            getSessionBoundHashOperations(sessionId).putAll(delta);
            delta = new HashMap<String,Object>(delta.size());

            expirationPolicy.onExpirationUpdated(originalLastAccessTime, this);
        }
    }
}