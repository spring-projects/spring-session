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

package org.springframework.session.hazelcast.config.annotation.web.http;

import java.time.Duration;
import java.time.Instant;

import com.hazelcast.core.HazelcastInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
import org.springframework.session.hazelcast.HazelcastITestUtils;
import org.springframework.session.hazelcast.SessionEventRegistry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensure that the appropriate SessionEvents are fired at the expected times. Additionally
 * ensure that the interactions with the {@link SessionRepository} abstraction behave as
 * expected after each SessionEvent.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class EnableHazelcastHttpSessionEventsTests<S extends Session> {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	@Autowired
	private SessionRepository<S> repository;

	@Autowired
	private SessionEventRegistry registry;

	@Before
	public void setup() {
		this.registry.clear();
	}

	@Test
	public void saveSessionTest() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		S sessionToSave = this.repository.createSession();

		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		sessionToSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username,
				"password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		sessionToSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
		sessionToSave.setAttribute(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

		this.repository.save(sessionToSave);

		assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
		assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
				.isInstanceOf(SessionCreatedEvent.class);

		Session session = this.repository.findById(sessionToSave.getId());

		assertThat(session.getId()).isEqualTo(sessionToSave.getId());
		assertThat(session.getAttributeNames())
				.isEqualTo(sessionToSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
				.isEqualTo(sessionToSave.getAttribute(expectedAttributeName));
	}

	@Test
	public void expiredSessionTest() throws InterruptedException {
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

		assertThat(this.repository.<Session>findById(sessionToSave.getId())).isNull();
	}

	@Test
	public void deletedSessionTest() throws InterruptedException {
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
	public void saveUpdatesTimeToLiveTest() throws InterruptedException {
		Object lock = new Object();

		S sessionToSave = this.repository.createSession();

		this.repository.save(sessionToSave);

		synchronized (lock) {
			lock.wait(sessionToSave.getMaxInactiveInterval().minusMillis(500).toMillis());
		}

		// Get and save the session like SessionRepositoryFilter would.
		S sessionToUpdate = this.repository.findById(sessionToSave.getId());
		sessionToUpdate.setLastAccessedTime(Instant.now());
		this.repository.save(sessionToUpdate);

		synchronized (lock) {
			lock.wait(sessionToUpdate.getMaxInactiveInterval().minusMillis(100).toMillis());
		}

		assertThat(this.repository.findById(sessionToUpdate.getId())).isNotNull();
	}

	@Test // gh-1077
	public void changeSessionIdNoEventTest() throws InterruptedException {
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

	@Configuration
	@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class HazelcastSessionConfig {

		@Bean
		public HazelcastInstance embeddedHazelcast() {
			return HazelcastITestUtils.embeddedHazelcastServer();
		}

		@Bean
		public SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}
	}

}
