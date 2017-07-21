/*
 * Copyright 2014-2017 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionStore;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author Rob Winch
 * @since 2.0
 */
class SpringSessionWebSessionStore<S extends Session> implements WebSessionStore {
	private final ReactorSessionRepository<S> sessions;

	SpringSessionWebSessionStore(ReactorSessionRepository<S> sessions) {
		Assert.notNull(sessions, "sessions cannot be null");
		this.sessions = sessions;
	}

	public Mono<WebSession> createSession() {
		return this.sessions.createSession().map(this::createSession);
	}

	public Mono<WebSession> setLastAccessedTime(WebSession session,
			Instant lastAccessedTime) {
		SpringSessionWebSession springSessionWebSession = (SpringSessionWebSession) session;
		springSessionWebSession.session.setLastAccessedTime(lastAccessedTime);
		return Mono.just(session);
	}

	@Override
	public Mono<Void> storeSession(WebSession session) {
		@SuppressWarnings("unchecked")
		SpringSessionWebSession springWebSession = (SpringSessionWebSession) session;
		return this.sessions.save(springWebSession.session);
	}

	@Override
	public Mono<WebSession> retrieveSession(String sessionId) {
		return this.sessions.findById(sessionId).map(this::existingSession);
	}

	@Override
	public Mono<Void> changeSessionId(String s, WebSession webSession) {
		return storeSession(webSession);
	}

	private SpringSessionWebSession createSession(S session) {
		return new SpringSessionWebSession(session, State.NEW);
	}

	private SpringSessionWebSession existingSession(S session) {
		return new SpringSessionWebSession(session, State.STARTED);
	}

	@Override
	public Mono<Void> removeSession(String sessionId) {
		return sessions.delete(sessionId);
	}

	private class SpringSessionWebSession implements WebSession {
		private final S session;

		private final Map<String, Object> attributes;

		private AtomicReference<State> state = new AtomicReference<>();

		private volatile transient Supplier<Mono<Void>> saveOperation = Mono::empty;

		SpringSessionWebSession(S session, State state) {
			Assert.notNull(session, "session cannot be null");
			this.session = session;
			this.attributes = new SpringSessionMap(session);
			this.state.set(state);
		}

		@Override
		public String getId() {
			return session.getId();
		}

		@Override
		public Mono<Void> changeSessionId() {
			return Mono.defer(() -> {
				session.changeSessionId();
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
		public Mono<Void> save() {
			return this.saveOperation.get();
		}

		@Override
		public boolean isExpired() {
			return this.session.isExpired();
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
		public void setMaxIdleTime(Duration maxIdleTime) {
			this.session.setMaxInactiveInterval(maxIdleTime);
		}

		@Override
		public Duration getMaxIdleTime() {
			return this.session.getMaxInactiveInterval();
		}
	}

	private enum State {
		NEW, STARTED
	}

	static class SpringSessionMap implements Map<String, Object> {
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
					.anyMatch(attrName -> this.session.getAttribute(attrName) != null);
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
			return values;
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
			public Iterator<Object> iterator() {
				return new Iterator<Object>() {
					private Iterator<Entry<String, Object>> i = entrySet().iterator();

					public boolean hasNext() {
						return i.hasNext();
					}

					public Object next() {
						return i.next().getValue();
					}

					public void remove() {
						i.remove();
					}
				};
			}

			public int size() {
				return SpringSessionMap.this.size();
			}

			public boolean isEmpty() {
				return SpringSessionMap.this.isEmpty();
			}

			public void clear() {
				SpringSessionMap.this.clear();
			}

			public boolean contains(Object v) {
				return SpringSessionMap.this.containsValue(v);
			}
		}
	}
}
