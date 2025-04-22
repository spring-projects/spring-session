/*
 * Copyright 2014-2025 the original author or authors.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensure that the appropriate SessionEvents are fired at the expected times. Additionally
 * ensure that the interactions with the {@link SessionRepository} abstraction behave as
 * expected after each SessionEvent.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 */
@SpringJUnitWebConfig
class SessionEventHazelcastIndexedSessionRepositoryTests<S extends Session> {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 2;

	@Autowired
	private SessionRepository<S> repository;

	@Autowired
	private SessionEventRegistry registry;

	@BeforeEach
	void setup() {
		this.registry.clear();
	}

	@Test
	void saveSessionTest() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		S sessionToSave = this.repository.createSession();

		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		sessionToSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		sessionToSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
		sessionToSave.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionCreatedEvent.class);

		Session session = this.repository.findById(sessionToSave.getId());

		assertThat(session.getId()).isEqualTo(sessionToSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(sessionToSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
			.isEqualTo(sessionToSave.getAttribute(expectedAttributeName));
	}

	@Test
	void expiredSessionTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession();

		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		assertThat(sessionToSave.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionExpiredEvent.class);

		assertThat(this.repository.findById(sessionToSave.getId())).isNull();
	}

	@Test
	void deletedSessionTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession();

		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		this.repository.deleteById(sessionToSave.getId());

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionDeletedEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionDeletedEvent.class);

		assertThat(this.repository.findById(sessionToSave.getId())).isNull();
	}

	@Test
	void saveUpdatesTimeToLiveTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession();
		sessionToSave.setMaxInactiveInterval(Duration.ofSeconds(3));
		this.repository.save(sessionToSave);

		Thread.sleep(2000);

		// Get and save the session like SessionRepositoryFilter would.
		S sessionToUpdate = this.repository.findById(sessionToSave.getId());
		sessionToUpdate.setLastAccessedTime(Instant.now());
		this.repository.save(sessionToUpdate);

		Thread.sleep(2000);

		assertThat(this.repository.findById(sessionToUpdate.getId())).isNotNull();
	}

	@Test // gh-1077
	void changeSessionIdNoEventTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession();
		sessionToSave.setMaxInactiveInterval(Duration.ofMinutes(30));

		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		sessionToSave.changeSessionId();
		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isFalse();
	}

	@Test // gh-1300
	void updateMaxInactiveIntervalTest() throws InterruptedException {
		S sessionToSave = this.repository.createSession();
		sessionToSave.setMaxInactiveInterval(Duration.ofMinutes(30));
		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
			.isInstanceOf(SessionCreatedEvent.class);
		this.registry.clear();

		S sessionToUpdate = this.repository.findById(sessionToSave.getId());
		sessionToUpdate.setLastAccessedTime(Instant.now());
		sessionToUpdate.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.repository.save(sessionToUpdate);

		assertThat(this.registry.receivedEvent(sessionToUpdate.getId())).isTrue();
		assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToUpdate.getId()))
			.isInstanceOf(SessionExpiredEvent.class);
		assertThat(this.repository.findById(sessionToUpdate.getId())).isNull();
	}

	@Configuration
	@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class HazelcastSessionConfig {

		@Bean
		HazelcastInstance embeddedHazelcast() {
			return HazelcastITestUtils.embeddedHazelcastServer();
		}

		@Bean
		SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}

	}

}
