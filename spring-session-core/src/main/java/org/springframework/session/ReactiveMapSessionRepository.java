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

package org.springframework.session;

import java.time.Duration;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * A {@link ReactiveSessionRepository} backed by a {@link Map} and that uses a
 * {@link MapSession}. The injected {@link java.util.Map} can be backed by a distributed
 * NoSQL store like Hazelcast, for instance. Note that the supplied map must be a
 * non-blocking map, and is itself responsible for purging the expired sessions.
 *
 * <p>
 * The implementation does NOT support firing {@link SessionDeletedEvent} or
 * {@link SessionExpiredEvent}.
 * </p>
 *
 * @author Rob Winch
 * @since 2.0
 */
public class ReactiveMapSessionRepository implements ReactiveSessionRepository<MapSession> {

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private final Map<String, Session> sessions;

	private SessionIdGenerationStrategy sessionIdGenerationStrategy = UuidSessionIdGenerationStrategy.getInstance();

	/**
	 * Creates a new instance backed by the provided {@link Map}. This allows injecting a
	 * distributed {@link Map}.
	 * @param sessions the {@link Map} to use. Cannot be null.
	 */
	public ReactiveMapSessionRepository(Map<String, Session> sessions) {
		if (sessions == null) {
			throw new IllegalArgumentException("sessions cannot be null");
		}
		this.sessions = sessions;
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

	@Override
	public Mono<Void> save(MapSession session) {
		return Mono.fromRunnable(() -> {
			if (!session.getId().equals(session.getOriginalId())) {
				this.sessions.remove(session.getOriginalId());
			}
			this.sessions.put(session.getId(), new MapSession(session));
		});
	}

	@Override
	public Mono<MapSession> findById(String id) {
		// @formatter:off
		return Mono.defer(() -> Mono.justOrEmpty(this.sessions.get(id))
				.filter((session) -> !session.isExpired())
				.map(MapSession::new)
				.doOnNext((session) -> session.setSessionIdGenerationStrategy(this.sessionIdGenerationStrategy))
				.switchIfEmpty(deleteById(id).then(Mono.empty())));
		// @formatter:on
	}

	@Override
	public Mono<Void> deleteById(String id) {
		return Mono.fromRunnable(() -> this.sessions.remove(id));
	}

	@Override
	public Mono<MapSession> createSession() {
		return Mono.defer(() -> {
			MapSession result = new MapSession(this.sessionIdGenerationStrategy);
			result.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
			return Mono.just(result);
		});
	}

	/**
	 * Sets the {@link SessionIdGenerationStrategy} to use.
	 * @param sessionIdGenerationStrategy the non-null {@link SessionIdGenerationStrategy}
	 * to use
	 * @since 3.2
	 */
	public void setSessionIdGenerationStrategy(SessionIdGenerationStrategy sessionIdGenerationStrategy) {
		Assert.notNull(sessionIdGenerationStrategy, "sessionIdGenerationStrategy cannot be null");
		this.sessionIdGenerationStrategy = sessionIdGenerationStrategy;
	}

}
