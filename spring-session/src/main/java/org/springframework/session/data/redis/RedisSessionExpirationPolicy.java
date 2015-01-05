/*
 * Copyright 2002-2013 the original author or authors.
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

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession;

/**
 * A strategy for expiring {@link RedisSession} instances. This performs two
 * operations:
 *
 * Redis has no guarantees of when an expired session event will be fired. In
 * order to ensure expired session events are processed in a timely fashion the
 * expiration (rounded to the nearest minute) is mapped to all the sessions that
 * expire at that time. Whenever {@link #cleanExpiredSessions()} is invoked, the
 * sessions for the previous minute are then deleted explicitly.
 *
 * In some instances the {@link #cleanExpiredSessions()} method may not be not
 * invoked for a specific time. For example, this may happen when a server is
 * restarted. To account for this, the expiration on the Redis session is also set.
 *
 * @author Rob Winch
 * @since 1.0
 */
final class RedisSessionExpirationPolicy {

    private static final Log logger = LogFactory.getLog(RedisOperationsSessionRepository.class);

    /**
     * The prefix for each key of the Redis Hash representing a single session. The suffix is the unique session id.
     */
    static final String EXPIRATION_BOUNDED_HASH_KEY_PREFIX = "spring:session:expirations:";

    private final RedisOperations<String,ExpiringSession> sessionRedisOperations;

    private final RedisOperations<String,String> expirationRedisOperations;

    public RedisSessionExpirationPolicy(
            RedisOperations<String, ExpiringSession> sessionRedisOperations,
            RedisOperations<String, String> expirationRedisOperations) {
        super();
        this.sessionRedisOperations = sessionRedisOperations;
        this.expirationRedisOperations = expirationRedisOperations;
    }

    public void onDelete(ExpiringSession session) {
        long lastAccessedTime = session.getLastAccessedTime();
        int maxInactiveInterval = session.getMaxInactiveIntervalInSeconds();

        long toExpire = roundUpToNextMinute(lastAccessedTime, maxInactiveInterval);
        String expireKey = getExpirationKey(toExpire);
        expirationRedisOperations.boundSetOps(expireKey).remove(session.getId());
    }

    public void onExpirationUpdated(Long originalExpirationTime, ExpiringSession session) {
        if(originalExpirationTime != null) {
            String expireKey = getExpirationKey(originalExpirationTime);
            expirationRedisOperations.boundSetOps(expireKey).remove(session.getId());
        }

        long toExpire = roundUpToNextMinute(session.getLastAccessedTime(), session.getMaxInactiveIntervalInSeconds());

        String expireKey = getExpirationKey(toExpire);
        expirationRedisOperations.boundSetOps(expireKey).add(session.getId());

        long redisExpirationInSeconds = session.getMaxInactiveIntervalInSeconds();
        String sessionKey = getSessionKey(session.getId());
        expirationRedisOperations.boundSetOps(expireKey).expire(redisExpirationInSeconds, TimeUnit.SECONDS);
        sessionRedisOperations.boundHashOps(sessionKey).expire(redisExpirationInSeconds, TimeUnit.SECONDS);
    }

    private String getExpirationKey(long expires) {
        return EXPIRATION_BOUNDED_HASH_KEY_PREFIX + expires;
    }

    private String getSessionKey(String sessionId) {
        return RedisOperationsSessionRepository.BOUNDED_HASH_KEY_PREFIX + sessionId;
    }

    public void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        long prevMin = roundDownMinute(now);

        if(logger.isDebugEnabled()) {
            logger.debug("Cleaning up sessions expiring at "+ new Date(prevMin));
        }

        String expirationKey = getExpirationKey(prevMin);
        Set<String> sessionsToExpire = expirationRedisOperations.boundSetOps(expirationKey).members();
        Set<String> keysToDelete = new HashSet<String>(sessionsToExpire.size() + 1);
        keysToDelete.add(expirationKey);
        for(String session : sessionsToExpire) {
            String sessionKey = getSessionKey(session);
            keysToDelete.add(sessionKey);
        }

        sessionRedisOperations.delete(keysToDelete);

        if(logger.isDebugEnabled()) {
            logger.debug("The following expired Sessions were deleted " + keysToDelete);
        }
    }

    private long roundUpToNextMinute(long timeInMs, int inactiveIntervalInSec) {

        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(timeInMs + TimeUnit.SECONDS.toMillis(inactiveIntervalInSec));
        date.add(Calendar.MINUTE, 1);
        date.clear(Calendar.SECOND);
        date.clear(Calendar.MILLISECOND);
        return date.getTimeInMillis();
    }

    private long roundDownMinute(long timeInMs) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(timeInMs);
        date.add(Calendar.MINUTE, -1);
        date.clear(Calendar.SECOND);
        date.clear(Calendar.MILLISECOND);
        return date.getTimeInMillis();
    }
}
