/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.session.redis;

import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Rob Winch
 */
public class RedisOperationsSessionRepository implements SessionRepository<RedisOperationsSessionRepository.RedisSession> {
    private final String BOUNDED_HASH_KEY_PREFIX = "spring-security-sessions:";
    private final String CREATION_TIME_ATTR = "creationTime";
    private final String MAX_INACTIVE_ATTR = "maxInactiveInterval";
    private final String LAST_ACCESSED_ATTR = "lastAccessedTime";
    private final String SESSION_ATTR_PREFIX = "sessionAttr:";


    private final RedisOperations<String,Session> redisTemplate;

    public RedisOperationsSessionRepository(RedisOperations<String, Session> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(RedisSession session) {
        session.saveDelta();
    }

    @Override
    public Session getSession(String id) {
        Map<Object, Object> entries = getOperations(id).entries();
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
        return new RedisSession(loaded);
    }

    @Override
    public void delete(String sessionId) {
        String key = getKey(sessionId);
        this.redisTemplate.delete(key);
    }

    @Override
    public RedisSession createSession() {
        return new RedisSession();
    }

    private String getKey(String sessionId) {
        return BOUNDED_HASH_KEY_PREFIX + sessionId;
    }

    private BoundHashOperations<String, Object, Object> getOperations(String sessionId) {
        String key = getKey(sessionId);
        return this.redisTemplate.boundHashOps(key);
    }

    class RedisSession implements Session {
        private final MapSession cached;
        private Map<String, Object> delta = new HashMap<String,Object>();

        private RedisSession() {
            this(new MapSession());
            delta.put(CREATION_TIME_ATTR, getCreationTime());
            delta.put(MAX_INACTIVE_ATTR, getMaxInactiveInterval());
            delta.put(LAST_ACCESSED_ATTR, getLastAccessedTime());
        }

        private RedisSession(MapSession cached) {
            this.cached = cached;
        }

        @Override
        public void setLastAccessedTime(long lastAccessedTime) {
            cached.setLastAccessedTime(lastAccessedTime);
            delta.put(LAST_ACCESSED_ATTR, getLastAccessedTime());
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
            delta.put(SESSION_ATTR_PREFIX + attributeName, attributeValue);
        }

        @Override
        public void removeAttribute(String attributeName) {
            cached.removeAttribute(attributeName);
            delta.put(SESSION_ATTR_PREFIX + attributeName, null);
        }

        private void saveDelta() {
            getOperations(getId()).putAll(delta);
            getOperations(getId()).expire(getMaxInactiveInterval(), TimeUnit.SECONDS);
            delta.clear();
        }
    }
}
