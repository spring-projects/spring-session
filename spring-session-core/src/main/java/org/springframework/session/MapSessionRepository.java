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

package org.springframework.session;

import java.time.Duration;
import java.util.Map;

import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

/**
 * A {@link SessionRepository} backed by a {@link java.util.Map} and that uses a
 * {@link MapSession}. The injected {@link java.util.Map} can be backed by a distributed
 * NoSQL store like Hazelcast, for instance. Note that the supplied map itself is
 * responsible for purging the expired sessions.
 *
 * <p>
 * The implementation does NOT support firing {@link SessionDeletedEvent} or
 * {@link SessionExpiredEvent}.
 * </p>
 *
 * @author Rob Winch
 * @since 1.0
 */
public class MapSessionRepository implements SessionRepository<MapSession> {

	/**
	 * If non-null, this value is used to override
	 * {@link Session#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private final Map<String, Session> sessions;

	/**
	 * Creates a new instance backed by the provided {@link java.util.Map}. This allows
	 * injecting a distributed {@link java.util.Map}.
	 *
	 * @param sessions the {@link java.util.Map} to use. Cannot be null.
	 */
	public MapSessionRepository(Map<String, Session> sessions) {
		if (sessions == null) {
			throw new IllegalArgumentException("sessions cannot be null");
		}
		this.sessions = sessions;
	}

	/**
	 * If non-null, this value is used to override
	 * {@link Session#setMaxInactiveInterval(Duration)}.
	 * @param defaultMaxInactiveInterval the number of seconds that the {@link Session}
	 * should be kept alive between client requests.
	 */
	public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	@Override
	public void save(MapSession session) {
		if (!session.getId().equals(session.getOriginalId())) {
			this.sessions.remove(session.getOriginalId());
		}
		this.sessions.put(session.getId(), new MapSession(session));
	}

	@Override
	public MapSession findById(String id) {
		Session saved = this.sessions.get(id);
		if (saved == null) {
			return null;
		}
		if (saved.isExpired()) {
			deleteById(saved.getId());
			return null;
		}
		return new MapSession(saved);
	}

	@Override
	public void deleteById(String id) {
		this.sessions.remove(id);
	}

	@Override
	public MapSession createSession() {
		MapSession result = new MapSession();
		if (this.defaultMaxInactiveInterval != null) {
			result.setMaxInactiveInterval(
					Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		return result;
	}

}
