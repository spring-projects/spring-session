/*
 * Copyright 2014-2023 the original author or authors.
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link ReactiveMapSessionRepository}.
 *
 * @author Rob Winch
 * @since 2.0
 */
class ReactiveMapSessionRepositoryTests {

	private ReactiveMapSessionRepository repository;

	private MapSession session;

	@BeforeEach
	void setup() {
		this.repository = new ReactiveMapSessionRepository(new HashMap<>());
		this.session = new MapSession("session-id");
	}

	@Test
	void constructorMapThenFound() {
		Map<String, Session> sessions = new HashMap<>();
		sessions.put(this.session.getId(), this.session);
		this.repository = new ReactiveMapSessionRepository(sessions);

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test
	void constructorMapWhenNullThenThrowsIllegalArgumentException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReactiveMapSessionRepository(null))
			.withMessage("sessions cannot be null");
	}

	@Test
	void saveWhenNoSubscribersThenNotFound() {
		this.repository.save(this.session);

		assertThat(this.repository.findById(this.session.getId()).block()).isNull();
	}

	@Test
	void saveWhenSubscriberThenFound() {
		this.repository.save(this.session).block();

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test
	void findByIdWhenExpiredRemovesFromSessionMap() {
		this.session.setMaxInactiveInterval(Duration.ofMinutes(1));
		this.session.setLastAccessedTime(Instant.now().minus(5, ChronoUnit.MINUTES));

		Map<String, Session> sessions = new ConcurrentHashMap<>();
		sessions.put("session-id", this.session);
		this.repository = new ReactiveMapSessionRepository(sessions);

		assertThat(this.repository.findById(this.session.getId()).block()).isNull();
		assertThat(sessions).isEmpty();
	}

	@Test
	void createSessionWhenDefaultMaxInactiveIntervalThenDefaultMaxInactiveInterval() {
		Session session = this.repository.createSession().block();

		assertThat(session).isInstanceOf(MapSession.class);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(new MapSession().getMaxInactiveInterval());
	}

	@Test
	void createSessionWhenCustomMaxInactiveIntervalThenCustomMaxInactiveInterval() {
		Duration expectedMaxInterval = new MapSession().getMaxInactiveInterval().plusSeconds(10);
		this.repository.setDefaultMaxInactiveInterval(expectedMaxInterval);

		Session session = this.repository.createSession().block();

		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInterval);
	}

	@Test
	void changeSessionIdWhenNotYetSaved() {
		MapSession createSession = this.repository.createSession().block();

		String originalId = createSession.getId();
		createSession.changeSessionId();

		this.repository.save(createSession).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(createSession.getId()).block()).isNotNull();
	}

	@Test
	void changeSessionIdWhenSaved() {
		MapSession createSession = this.repository.createSession().block();

		this.repository.save(createSession).block();

		String originalId = createSession.getId();
		createSession.changeSessionId();

		this.repository.save(createSession).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(createSession.getId()).block()).isNotNull();
	}

	@Test // gh-1120
	void getAttributeNamesAndRemove() {
		MapSession session = this.repository.createSession().block();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	void createSessionWhenSessionIdGeneratorThenUses() {
		this.repository.setSessionIdGenerator(() -> "test");
		MapSession session = this.repository.createSession().block();
		assertThat(session.getId()).isEqualTo("test");
		assertThat(session.changeSessionId()).isEqualTo("test");
	}

	@Test
	void setSessionIdGeneratorWhenNullThenThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setSessionIdGenerator(null))
			.withMessage("sessionIdGenerator cannot be null");
	}

	@Test
	void findByIdWhenChangeSessionIdThenUsesSessionIdGenerator() {
		this.repository.setSessionIdGenerator(() -> "test");

		MapSession session = this.repository.createSession().block();
		this.repository.save(session).block();

		MapSession savedSession = this.repository.findById("test").block();

		assertThat(savedSession.getId()).isEqualTo("test");
		assertThat(savedSession.changeSessionId()).isEqualTo("test");
	}

}
