/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

/**
 * A {@link SessionRepository} backed by a {@link java.util.Map} and that uses a
 * {@link MapSession}. By default a {@link java.util.concurrent.ConcurrentHashMap} is
 * used, but a custom {@link java.util.Map} can be injected to use distributed maps
 * provided by NoSQL stores like Redis and Hazelcast.
 *
 * <p>
 * The implementation does NOT support firing {@link SessionDeletedEvent} or
 * {@link SessionExpiredEvent}.
 * </p>
 *
 * @author Rob Winch
 * @since 1.0
 */
public class MapSessionRepository implements SessionRepository<ExpiringSession> {
	/**
	 * If non-null, this value is used to override
	 * {@link ExpiringSession#setMaxInactiveIntervalInSeconds(int)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private final Map<String, ExpiringSession> sessions;

	/**
	 * Creates an instance backed by a {@link java.util.concurrent.ConcurrentHashMap}.
	 */
	public MapSessionRepository() {
		this(new ConcurrentHashMap<String, ExpiringSession>());
	}

	/**
	 * Creates a new instance backed by the provided {@link java.util.Map}. This allows
	 * injecting a distributed {@link java.util.Map}.
	 *
	 * @param sessions the {@link java.util.Map} to use. Cannot be null.
	 */
	public MapSessionRepository(Map<String, ExpiringSession> sessions) {
		if (sessions == null) {
			throw new IllegalArgumentException("sessions cannot be null");
		}
		this.sessions = sessions;
	}

	/**
	 * If non-null, this value is used to override
	 * {@link ExpiringSession#setMaxInactiveIntervalInSeconds(int)}.
	 * @param defaultMaxInactiveInterval the number of seconds that the {@link Session}
	 * should be kept alive between client requests.
	 */
	public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = Integer.valueOf(defaultMaxInactiveInterval);
	}

	public void save(ExpiringSession session) {
		this.sessions.put(session.getId(), new MapSession(session));
	}

	public ExpiringSession getSession(String id) {
		ExpiringSession saved = this.sessions.get(id);
		if (saved == null) {
			return null;
		}
		if (saved.isExpired()) {
			delete(saved.getId());
			return null;
		}
		return new MapSession(saved);
	}

	public void delete(String id) {
		this.sessions.remove(id);
	}

	public ExpiringSession createSession() {
		ExpiringSession result = new MapSession();
		if (this.defaultMaxInactiveInterval != null) {
			result.setMaxInactiveIntervalInSeconds(this.defaultMaxInactiveInterval);
		}
		return result;
	}
}
