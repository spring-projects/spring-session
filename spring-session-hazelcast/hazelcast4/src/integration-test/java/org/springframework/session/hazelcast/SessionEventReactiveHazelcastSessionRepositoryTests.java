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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.hazelcast.config.annotation.web.server.EnableHazelcastWebSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensure that the appropriate SessionEvents are fired at the expected times. Additionally
 * ensure that the interactions with the {@link SessionRepository} abstraction behave as
 * expected after each SessionEvent.
 *
 * @author Eleftheria Stein
 * @author Didier Loiseau
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class SessionEventReactiveHazelcastSessionRepositoryTests<S extends Session> {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 2;

	@Autowired
	private ReactiveSessionRepository<S> repository;

	@Autowired
	private SessionEventRegistry registry;

	@BeforeEach
	void setup() {
		this.registry.clear();
	}

	@Test
	void saveSessionTest() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		S sessionToSave = this.repository.createSession().block();

		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		sessionToSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		sessionToSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
		sessionToSave.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);

		Session session = this.repository.findById(sessionToSave.getId()).block();

		assertThat(session.getId()).isEqualTo(sessionToSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(sessionToSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
				.isEqualTo(sessionToSave.getAttribute(expectedAttributeName));
	}

	@Test
	void expiredSessionTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession().block();

		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		assertThat(sessionToSave.getMaxInactiveInterval())
				.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionExpiredEvent.class);

		assertThat(this.repository.findById(sessionToSave.getId()).block()).isNull();
	}

	@Test
	void deletedSessionTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession().block();

		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		this.repository.deleteById(sessionToSave.getId()).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionDeletedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionDeletedEvent.class);

		assertThat(this.repository.findById(sessionToSave.getId()).block()).isNull();
	}

	@Test
	void saveUpdatesTimeToLiveTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession().block();
		sessionToSave.setMaxInactiveInterval(Duration.ofSeconds(3));
		this.repository.save(sessionToSave).block();

		Thread.sleep(2000);

		// Get and save the session like SessionRepositoryFilter would.
		S sessionToUpdate = this.repository.findById(sessionToSave.getId()).block();
		sessionToUpdate.setLastAccessedTime(Instant.now());
		this.repository.save(sessionToUpdate).block();

		Thread.sleep(2000);

		assertThat(this.repository.findById(sessionToUpdate.getId()).block()).isNotNull();
	}

	@Test // gh-1077
	void changeSessionIdNoEventTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession().block();
		sessionToSave.setMaxInactiveInterval(Duration.ofMinutes(30));

		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		sessionToSave.changeSessionId();
		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isFalse();
	}

	@Test // gh-1300
	void updateMaxInactiveIntervalTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession().block();
		sessionToSave.setMaxInactiveInterval(Duration.ofMinutes(30));
		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		S sessionToUpdate = this.repository.findById(sessionToSave.getId()).block();
		sessionToUpdate.setLastAccessedTime(Instant.now());
		sessionToUpdate.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.repository.save(sessionToUpdate).block();

		assertThat(this.registry.receivedEvent(sessionToUpdate.getId())).isTrue();
		assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToUpdate.getId()))
				.isInstanceOf(SessionExpiredEvent.class);
		assertThat(this.repository.findById(sessionToUpdate.getId()).block()).isNull();
	}

	@Test // gh-1899
	void updateSessionAndExpireAfterOriginalTimeToLiveTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession().block();
		this.repository.save(sessionToSave).block();

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		S sessionToUpdate = this.repository.findById(sessionToSave.getId()).block();
		sessionToUpdate.setLastAccessedTime(Instant.now());
		this.repository.save(sessionToUpdate).block();

		assertThat(this.registry.receivedEvent(sessionToUpdate.getId())).isTrue();
		assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToUpdate.getId()))
				.isInstanceOf(SessionExpiredEvent.class);
		// Assert this after the expired event was received because it would otherwise do
		// its own expiration check and explicitly delete the session from Hazelcast
		// regardless of the TTL of the IMap entry.
		assertThat(this.repository.findById(sessionToUpdate.getId()).block()).isNull();
	}

	@Configuration
	@EnableHazelcastWebSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class HazelcastSessionConfig {

		@Bean
		HazelcastInstance embeddedHazelcast() {
			return Hazelcast4ITestUtils.embeddedHazelcastServer();
		}

		@Bean
		SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}

	}

}
