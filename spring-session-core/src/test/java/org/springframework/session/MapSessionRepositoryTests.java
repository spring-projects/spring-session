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
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MapSessionRepository}.
 */
public class MapSessionRepositoryTests {

	private MapSessionRepository repository;

	private MapSession session;

	@Before
	public void setup() {
		this.repository = new MapSessionRepository(new ConcurrentHashMap<>());
		this.session = new MapSession();
	}

	@Test
	public void getSessionExpired() {
		this.session.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.session.setLastAccessedTime(Instant.now().minus(5, ChronoUnit.MINUTES));
		this.repository.save(this.session);

		assertThat(this.repository.findById(this.session.getId())).isNull();
	}

	@Test
	public void createSessionDefaultExpiration() {
		Session session = this.repository.createSession();

		assertThat(session).isInstanceOf(MapSession.class);
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(new MapSession().getMaxInactiveInterval());
	}

	@Test
	public void createSessionCustomDefaultExpiration() {
		final Duration expectedMaxInterval = new MapSession().getMaxInactiveInterval()
				.plusSeconds(10);
		this.repository.setDefaultMaxInactiveInterval(
				(int) expectedMaxInterval.getSeconds());

		Session session = this.repository.createSession();

		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(expectedMaxInterval);
	}

	@Test
	public void changeSessionIdWhenNotYetSaved() {
		MapSession createSession = this.repository.createSession();

		String originalId = createSession.getId();
		createSession.changeSessionId();

		this.repository.save(createSession);

		assertThat(this.repository.findById(originalId)).isNull();
		assertThat(this.repository.findById(createSession.getId())).isNotNull();
	}

	@Test
	public void changeSessionIdWhenSaved() {
		MapSession createSession = this.repository.createSession();

		this.repository.save(createSession);

		String originalId = createSession.getId();
		createSession.changeSessionId();

		this.repository.save(createSession);

		assertThat(this.repository.findById(originalId)).isNull();
		assertThat(this.repository.findById(createSession.getId())).isNotNull();
	}

	@Test // gh-1120
	public void getAttributeNamesAndRemove() {
		MapSession session = this.repository.createSession();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

}
