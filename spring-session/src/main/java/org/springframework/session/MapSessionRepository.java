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
package org.springframework.session;

import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SessionRepository} backed by a {@link java.util.Map} and that uses a {@link MapSession}. By default a
 * {@link java.util.concurrent.ConcurrentHashMap} is used, but a custom {@link java.util.Map} can be injected to use
 * distributed maps provided by NoSQL stores like Redis and Hazelcast.
 *
 * @author Rob Winch
 * @since 1.0
 */
public class MapSessionRepository implements SessionRepository<Session> {
    private final Map<String,Session> sessions;

    /**
     * Creates an instance backed by a {@link java.util.concurrent.ConcurrentHashMap}
     */
    public MapSessionRepository() {
        this(new ConcurrentHashMap<String, Session>());
    }

    /**
     * Creates a new instance backed by the provided {@link java.util.Map}. This allows injecting a distributed {@link java.util.Map}.
     *
     * @param sessions the {@link java.util.Map} to use. Cannot be null.
     */
    public MapSessionRepository(Map<String,Session> sessions) {
        Assert.notNull(sessions, "sessions cannot be null");
        this.sessions = sessions;
    }

    public void save(Session session) {
        sessions.put(session.getId(), new MapSession(session));
    }

    public Session getSession(String id) {
        Session result = sessions.get(id);
        return result == null ? null : new MapSession(result);
    }

    public void delete(String id) {
        sessions.remove(id);
    }

    public Session createSession() {
        return new MapSession();
    }
}
