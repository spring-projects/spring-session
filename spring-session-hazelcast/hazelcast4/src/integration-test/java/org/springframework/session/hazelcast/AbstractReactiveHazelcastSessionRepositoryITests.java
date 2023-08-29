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

package org.springframework.session.hazelcast;

import java.time.Duration;
import java.time.Instant;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.MapSession;
import org.springframework.session.hazelcast.ReactiveHazelcastSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for {@link ReactiveHazelcastSessionRepository} integration tests.
 *
 * @author Eleftheria Stein
 * @author Didier Loiseau
 */
abstract class AbstractReactiveHazelcastSessionRepositoryITests {

	@Autowired
	private HazelcastInstance hazelcastInstance;

	@Autowired
	private ReactiveHazelcastSessionRepository repository;

	@Test
	void createAndDestroySession() {
		HazelcastSession sessionToSave = this.repository.createSession().block();
		String sessionId = sessionToSave.getId();

		IMap<String, MapSession> hazelcastMap = this.hazelcastInstance
				.getMap(ReactiveHazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME);

		this.repository.save(sessionToSave).block();

		assertThat(hazelcastMap.get(sessionId)).isEqualTo(sessionToSave);

		this.repository.deleteById(sessionId).block();

		assertThat(hazelcastMap.get(sessionId)).isNull();
	}

	@Test
	void changeSessionIdWhenOnlyChangeId() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		HazelcastSession toSave = this.repository.createSession().block();
		toSave.setAttribute(attrName, attrValue);

		this.repository.save(toSave).block();

		HazelcastSession findById = this.repository.findById(toSave.getId()).block();

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		HazelcastSession findByChangeSessionId = this.repository.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);

		this.repository.deleteById(changeSessionId).block();
	}

	@Test
	void changeSessionIdWhenChangeTwice() {
		HazelcastSession toSave = this.repository.createSession().block();

		this.repository.save(toSave).block();

		String originalId = toSave.getId();
		String changeId1 = toSave.changeSessionId();
		String changeId2 = toSave.changeSessionId();

		this.repository.save(toSave).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(changeId1).block()).isNull();
		assertThat(this.repository.findById(changeId2).block()).isNotNull();

		this.repository.deleteById(changeId2).block();
	}

	@Test
	void changeSessionIdWhenSetAttributeOnChangedSession() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		HazelcastSession toSave = this.repository.createSession().block();

		this.repository.save(toSave).block();

		HazelcastSession findById = this.repository.findById(toSave.getId()).block();

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		HazelcastSession findByChangeSessionId = this.repository.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);

		this.repository.deleteById(changeSessionId).block();
	}

	@Test
	void changeSessionIdWhenHasNotSaved() {
		HazelcastSession toSave = this.repository.createSession().block();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();

		this.repository.deleteById(toSave.getId()).block();
	}

	@Test // gh-1076
	void attemptToUpdateSessionAfterDelete() {
		HazelcastSession session = this.repository.createSession().block();
		String sessionId = session.getId();
		this.repository.save(session).block();
		session = this.repository.findById(sessionId).block();
		session.setAttribute("attributeName", "attributeValue");
		this.repository.deleteById(sessionId).block();
		this.repository.save(session).block();

		assertThat(this.repository.findById(sessionId).block()).isNull();
	}

	@Test
	void createAndUpdateSession() {
		HazelcastSession session = this.repository.createSession().block();
		String sessionId = session.getId();

		this.repository.save(session).block();

		session = this.repository.findById(sessionId).block();
		session.setAttribute("attributeName", "attributeValue");

		this.repository.save(session).block();

		assertThat(this.repository.findById(sessionId).block()).isNotNull()
				.extracting((s) -> s.getAttribute("attributeName")).isEqualTo("attributeValue");

		this.repository.deleteById(sessionId).block();
	}

	@Test
	void createAndUpdateSessionWhileKeepingOriginalTimeToLiveConfiguredOnRepository() {
		final Duration defaultSessionTimeout = Duration.ofSeconds(1800);

		final IMap<String, MapSession> hazelcastMap = this.hazelcastInstance
				.getMap(ReactiveHazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME);

		HazelcastSession session = this.repository.createSession().block();
		String sessionId = session.getId();
		this.repository.save(session).block();

		assertThat(session.getMaxInactiveInterval()).isEqualTo(defaultSessionTimeout);
		assertThat(hazelcastMap.getEntryView(sessionId).getTtl()).isEqualTo(defaultSessionTimeout.toMillis());

		session = this.repository.findById(sessionId).block();
		session.setLastAccessedTime(Instant.now());
		this.repository.save(session).block();

		session = this.repository.findById(sessionId).block();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(defaultSessionTimeout);
		assertThat(hazelcastMap.getEntryView(sessionId).getTtl()).isEqualTo(defaultSessionTimeout.toMillis());
	}

	@Test
	void createAndUpdateSessionWhileKeepingTimeToLiveSetOnSession() {
		final Duration individualSessionTimeout = Duration.ofSeconds(23);

		final IMap<String, MapSession> hazelcastMap = this.hazelcastInstance
				.getMap(ReactiveHazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME);

		HazelcastSession session = this.repository.createSession().block();
		session.setMaxInactiveInterval(individualSessionTimeout);
		String sessionId = session.getId();
		this.repository.save(session).block();

		assertThat(session.getMaxInactiveInterval()).isEqualTo(individualSessionTimeout);
		assertThat(hazelcastMap.getEntryView(sessionId).getTtl()).isEqualTo(individualSessionTimeout.toMillis());

		session = this.repository.findById(sessionId).block();
		session.setAttribute("attribute", "value");
		this.repository.save(session).block();

		session = this.repository.findById(sessionId).block();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(individualSessionTimeout);
		assertThat(hazelcastMap.getEntryView(sessionId).getTtl()).isEqualTo(individualSessionTimeout.toMillis());
	}

}
