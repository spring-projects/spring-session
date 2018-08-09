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

package org.springframework.session.web.server.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionStore;

/**
 * The {@link WebSessionStore} implementation that provides the {@link WebSession}
 * implementation backed by a {@link Session} returned by the
 * {@link ReactiveSessionRepository}.
 *
 * @param <S> the {@link Session} type
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 2.0
 */
public class SpringSessionWebSessionStore<S extends Session> implements WebSessionStore {

	private final ReactiveSessionRepository<S> sessions;

	private Clock clock = Clock.system(ZoneOffset.UTC);

	public SpringSessionWebSessionStore(ReactiveSessionRepository<S> reactiveSessionRepository) {
		Assert.notNull(reactiveSessionRepository, "reactiveSessionRepository cannot be null");
		this.sessions = reactiveSessionRepository;
	}

	/**
	 * Configure the {@link Clock} to use to set lastAccessTime on every created
	 * session and to calculate if it is expired.
	 * <p>This may be useful to align to different timezone or to set the clock
	 * back in a test, e.g. {@code Clock.offset(clock, Duration.ofMinutes(-31))}
	 * in order to simulate session expiration.
	 * <p>By default this is {@code Clock.system(ZoneId.of("GMT"))}.
	 * @param clock the clock to use
	 */
	public void setClock(Clock clock) {
		Assert.notNull(clock, "clock cannot be null");
		this.clock = clock;
	}

	@Override
	public Mono<WebSession> createWebSession() {
		return this.sessions.createSession().map(this::createSession);
	}

	@Override
	public Mono<WebSession> updateLastAccessTime(WebSession session) {
		@SuppressWarnings("unchecked")
		SpringSessionWebSession springSessionWebSession = (SpringSessionWebSession) session;
		springSessionWebSession.session.setLastAccessedTime(this.clock.instant());
		return Mono.just(session);
	}

	@Override
	public Mono<WebSession> retrieveSession(String sessionId) {
		return this.sessions.findById(sessionId)
				.doOnNext((session) -> session.setLastAccessedTime(this.clock.instant()))
				.map(this::existingSession);
	}

	@Override
	public Mono<Void> removeSession(String sessionId) {
		return this.sessions.deleteById(sessionId);
	}

	private SpringSessionWebSession createSession(S session) {
		return new SpringSessionWebSession(session, State.NEW);
	}

	private SpringSessionWebSession existingSession(S session) {
		return new SpringSessionWebSession(session, State.STARTED);
	}

	/**
	 * Adapts Spring Session's {@link Session} to a {@link WebSession}.
	 */
	private class SpringSessionWebSession implements WebSession {

		private final S session;

		private final Map<String, Object> attributes;

		private AtomicReference<State> state = new AtomicReference<>();

		SpringSessionWebSession(S session, State state) {
			Assert.notNull(session, "session cannot be null");
			this.session = session;
			this.attributes = new SpringSessionMap(session);
			this.state.set(state);
		}

		@Override
		public String getId() {
			return this.session.getId();
		}

		@Override
		public Mono<Void> changeSessionId() {
			return Mono.defer(() -> {
				this.session
						.changeSessionId();
				return save();
			});
		}

		@Override
		public Map<String, Object> getAttributes() {
			return this.attributes;
		}

		@Override
		public void start() {
			this.state.compareAndSet(State.NEW, State.STARTED);
		}

		@Override
		public boolean isStarted() {
			State value = this.state.get();
			return (State.STARTED.equals(value)
					|| (State.NEW.equals(value) && !getAttributes().isEmpty()));
		}

		@Override
		public Mono<Void> invalidate() {
			this.state.set(State.EXPIRED);
			return SpringSessionWebSessionStore.this.sessions.deleteById(this.session.getId());
		}

		@Override
		public Mono<Void> save() {
			return SpringSessionWebSessionStore.this.sessions.save(this.session);
		}

		@Override
		public boolean isExpired() {
			if (this.state.get().equals(State.EXPIRED)) {
				return true;
			}
			if (this.session.isExpired()) {
				this.state.set(State.EXPIRED);
				return true;
			}
			return false;
		}

		@Override
		public Instant getCreationTime() {
			return this.session.getCreationTime();
		}

		@Override
		public Instant getLastAccessTime() {
			return this.session.getLastAccessedTime();
		}

		@Override
		public Duration getMaxIdleTime() {
			return this.session.getMaxInactiveInterval();
		}

		@Override
		public void setMaxIdleTime(Duration maxIdleTime) {
			this.session.setMaxInactiveInterval(maxIdleTime);
		}
	}

	private enum State {
		NEW, STARTED, EXPIRED
	}

	private static class SpringSessionMap implements Map<String, Object> {

		private final Session session;

		private final Collection<Object> values = new SessionValues();

		SpringSessionMap(Session session) {
			this.session = session;
		}

		@Override
		public int size() {
			return this.session.getAttributeNames().size();
		}

		@Override
		public boolean isEmpty() {
			return this.session.getAttributeNames().isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			return key instanceof String
					&& this.session.getAttributeNames().contains(key);
		}

		@Override
		public boolean containsValue(Object value) {
			return this.session.getAttributeNames().stream()
					.anyMatch((attrName) -> this.session.getAttribute(attrName) != null);
		}

		@Override
		@Nullable
		public Object get(Object key) {
			if (key instanceof String) {
				return this.session.getAttribute((String) key);
			}
			return null;
		}

		@Override
		public Object put(String key, Object value) {
			Object original = this.session.getAttribute(key);
			this.session.setAttribute(key, value);
			return original;
		}

		@Override
		@Nullable
		public Object remove(Object key) {
			if (key instanceof String) {
				String attrName = (String) key;
				Object original = this.session.getAttribute(attrName);
				this.session.removeAttribute(attrName);
				return original;
			}
			return null;
		}

		@Override
		public void putAll(Map<? extends String, ?> m) {
			for (Entry<? extends String, ?> entry : m.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
		}

		@Override
		public void clear() {
			for (String attrName : this.session.getAttributeNames()) {
				remove(attrName);
			}
		}

		@Override
		public Set<String> keySet() {
			return this.session.getAttributeNames();
		}

		@Override
		public Collection<Object> values() {
			return this.values;
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			Set<String> attrNames = keySet();
			Set<Entry<String, Object>> entries = new HashSet<>(attrNames.size());
			for (String attrName : attrNames) {
				Object value = this.session.getAttribute(attrName);
				entries.add(new AbstractMap.SimpleEntry<>(attrName, value));
			}
			return Collections.unmodifiableSet(entries);
		}

		private class SessionValues extends AbstractCollection<Object> {

			@Override
			public Iterator<Object> iterator() {
				return new Iterator<Object>() {

					private Iterator<Entry<String, Object>> i = entrySet().iterator();

					@Override
					public boolean hasNext() {
						return this.i.hasNext();
					}

					@Override
					public Object next() {
						return this.i.next().getValue();
					}

					@Override
					public void remove() {
						this.i.remove();
					}

				};
			}

			@Override
			public int size() {
				return SpringSessionMap.this.size();
			}

			@Override
			public boolean isEmpty() {
				return SpringSessionMap.this.isEmpty();
			}

			@Override
			public void clear() {
				SpringSessionMap.this.clear();
			}

			@Override
			public boolean contains(Object v) {
				return SpringSessionMap.this.containsValue(v);
			}

		}
	}
}
