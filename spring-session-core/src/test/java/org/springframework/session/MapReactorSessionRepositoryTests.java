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

package org.springframework.session;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Rob Winch
 * @since 2.0
 */
public class MapReactorSessionRepositoryTests {
	MapReactorSessionRepository repository;

	MapSession session;

	@Before
	public void setup() {
		this.repository = new MapReactorSessionRepository();
		this.session = new MapSession("session-id");
	}

	@Test
	public void constructorVarargsThenFound() {
		this.repository = new MapReactorSessionRepository(this.session);

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorVarargsWhenNullThenThrowsIllegalArgumentException() {
		Session[] sessions = null;
		new MapReactorSessionRepository(sessions);
	}

	@Test
	public void constructorIterableThenFound() {
		this.repository = new MapReactorSessionRepository(Arrays.asList(this.session));

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorIterableWhenNullThenThrowsIllegalArgumentException() {
		Iterable<Session> sessions = null;
		new MapReactorSessionRepository(sessions);
	}

	@Test
	public void constructorMapThenFound() {
		Map<String, Session> sessions = new HashMap<>();
		sessions.put(this.session.getId(), this.session);
		this.repository = new MapReactorSessionRepository(sessions);

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorMapWhenNullThenThrowsIllegalArgumentException() {
		Map<String, Session> sessions = null;
		new MapReactorSessionRepository(sessions);
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
	public void findByIdWhenNotExpiredThenFound() {
		this.repository = new MapReactorSessionRepository(this.session);

		Session findByIdSession = this.repository.findById(this.session.getId()).block();
		assertThat(findByIdSession).isNotNull();
		assertThat(findByIdSession.getId()).isEqualTo(this.session.getId());
	}

	@Test
	public void findByIdWhenExpiredThenEmpty() {
		this.session.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.session.setLastAccessedTime(Instant.now().minus(5, ChronoUnit.MINUTES));
		this.repository = new MapReactorSessionRepository(Arrays.asList(this.session));

		assertThat(this.repository.findById(this.session.getId()).block()).isNull();
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
}
