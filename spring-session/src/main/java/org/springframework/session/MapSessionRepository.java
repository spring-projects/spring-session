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
package org.springframework.session;

import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SessionRepository} backed by a {@link java.util.Map} and that uses a {@link MapSession}. By default a
 * {@link java.util.concurrent.ConcurrentHashMap} is used, but a custom {@link java.util.Map} can be injected to use
 * distributed maps provided by NoSQL stores like Redis and Hazelcast.
 *
 * <p>
 * The implementation does NOT support firing {@link SessionDestroyedEvent}.
 * </p>
 *
 * @author Rob Winch
 * @since 1.0
 */
public class MapSessionRepository implements SessionRepository<ExpiringSession> {
    private final Map<String,ExpiringSession> sessions;

    /**
     * Creates an instance backed by a {@link java.util.concurrent.ConcurrentHashMap}
     */
    public MapSessionRepository() {
        this(new ConcurrentHashMap<String, ExpiringSession>());
    }

    /**
     * Creates a new instance backed by the provided {@link java.util.Map}. This allows injecting a distributed {@link java.util.Map}.
     *
     * @param sessions the {@link java.util.Map} to use. Cannot be null.
     */
    public MapSessionRepository(Map<String,ExpiringSession> sessions) {
        Assert.notNull(sessions, "sessions cannot be null");
        this.sessions = sessions;
    }

    public void save(ExpiringSession session) {
        sessions.put(session.getId(), new MapSession(session));
    }

    public ExpiringSession getSession(String id) {
        ExpiringSession saved = sessions.get(id);
        if(saved == null) {
            return null;
        }
        MapSession result = new MapSession(saved);
        result.setLastAccessedTime(System.currentTimeMillis());
        return result;
    }

    public void delete(String id) {
        sessions.remove(id);
    }

    public ExpiringSession createSession() {
        return new MapSession();
    }
}
