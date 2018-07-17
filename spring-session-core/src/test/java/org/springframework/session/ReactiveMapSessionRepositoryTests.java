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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ReactiveMapSessionRepository}.
 *
 * @author Rob Winch
 * @since 2.0
 */
public class ReactiveMapSessionRepositoryTests {

	private ReactiveMapSessionRepository repository;

	private MapSession session;

	@Before
	public void setup() {
		this.repository = new ReactiveMapSessionRepository(new HashMap<>());
		this.session = new MapSession("session-id");
	}

	@Test
	public void constructorMapThenFound() {
		Map<String, Session> sessions = new HashMap<>();
		sessions.put(this.session.getId(), this.session);
		this.repository = new ReactiveMapSessionRepository(sessions);

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test
	public void constructorMapWhenNullThenThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> new ReactiveMapSessionRepository(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("sessions cannot be null");
	}

	@Test
	public void saveWhenNoSubscribersThenNotFound() {
		this.repository.save(this.session);

		assertThat(this.repository.findById(this.session.getId()).block()).isNull();
	}

	@Test
	public void saveWhenSubscriberThenFound() {
		this.repository.save(this.session).block();

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test
	public void findByIdWhenExpiredRemovesFromSessionMap() {
		this.session.setMaxInactiveInterval(Duration.ofMinutes(1));
		this.session.setLastAccessedTime(Instant.now().minus(5, ChronoUnit.MINUTES));

		Map<String, Session> sessions = new ConcurrentHashMap<>();
		sessions.put("session-id", this.session);
		this.repository = new ReactiveMapSessionRepository(sessions);

		assertThat(this.repository.findById(this.session.getId()).block()).isNull();
		assertThat(sessions).isEmpty();
	}

	@Test
	public void createSessionWhenDefaultMaxInactiveIntervalThenDefaultMaxInactiveInterval() {
		Session session = this.repository.createSession().block();

		assertThat(session).isInstanceOf(MapSession.class);
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(new MapSession().getMaxInactiveInterval());
	}

	@Test
	public void createSessionWhenCustomMaxInactiveIntervalThenCustomMaxInactiveInterval() {
		final Duration expectedMaxInterval = new MapSession().getMaxInactiveInterval()
				.plusSeconds(10);
		this.repository.setDefaultMaxInactiveInterval(
				(int) expectedMaxInterval.getSeconds());

		Session session = this.repository.createSession().block();

		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(expectedMaxInterval);
	}

	@Test
	public void changeSessionIdWhenNotYetSaved() {
		MapSession createSession = this.repository.createSession().block();

		String originalId = createSession.getId();
		createSession.changeSessionId();

		this.repository.save(createSession).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(createSession.getId()).block()).isNotNull();
	}

	@Test
	public void changeSessionIdWhenSaved() {
		MapSession createSession = this.repository.createSession().block();

		this.repository.save(createSession).block();

		String originalId = createSession.getId();
		createSession.changeSessionId();

		this.repository.save(createSession).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(createSession.getId()).block()).isNotNull();
	}

	@Test // gh-1120
	public void getAttributeNamesAndRemove() {
		MapSession session = this.repository.createSession().block();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

}
