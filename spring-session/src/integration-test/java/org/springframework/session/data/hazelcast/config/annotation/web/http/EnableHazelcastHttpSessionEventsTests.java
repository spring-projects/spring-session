/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.data.hazelcast.config.annotation.web.http;

import static org.fest.assertions.Assertions.assertThat;

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
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.SessionEventRegistry;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.SocketUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Ensure that the appropriate SessionEvents are fired at the expected times.
 * Additionally ensure that the interactions with the {@link SessionRepository} 
 * abstraction behave as expected after each SessionEvent.
 * 
 * @author Tommy Ludwig
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class EnableHazelcastHttpSessionEventsTests<S extends ExpiringSession> {
	
	private final static String MAX_INACTIVE_INTERVAL_IN_SECONDS_STR = "1";
	private final static int MAX_INACTIVE_INTERVAL_IN_SECONDS = Integer.valueOf(MAX_INACTIVE_INTERVAL_IN_SECONDS_STR);
	
	@Autowired
	private SessionRepository<S> repository;
	
	@Autowired
	private SessionEventRegistry registry;
	
	private final Object lock = new Object();
	
	@Before
	public void setup() {
		registry.clear();
		registry.setLock(lock);
	}
	
	@Test
	public void saveSessionTest() throws InterruptedException {
		String username = "saves-"+System.currentTimeMillis();
		
		S sessionToSave = repository.createSession();
		
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		sessionToSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username,"password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		sessionToSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
		sessionToSave.setAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME, username);
		
		repository.save(sessionToSave);
		
		assertThat(registry.receivedEvent()).isTrue();
		assertThat(registry.getEvent()).isInstanceOf(SessionCreatedEvent.class);
		
		Session session = repository.getSession(sessionToSave.getId());

		assertThat(session.getId()).isEqualTo(sessionToSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(sessionToSave.getAttributeNames());
		assertThat(session.getAttribute(expectedAttributeName)).isEqualTo(sessionToSave.getAttribute(expectedAttributeName));
	}
	
	@Test
	public void expiredSessionTest() throws InterruptedException {
		S sessionToSave = repository.createSession();
		
		repository.save(sessionToSave);
		
		assertThat(registry.receivedEvent()).isTrue();
		assertThat(registry.getEvent()).isInstanceOf(SessionCreatedEvent.class);
		registry.clear();
		
		assertThat(sessionToSave.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		
		synchronized (lock) {
			lock.wait((sessionToSave.getMaxInactiveIntervalInSeconds() * 1000) + 1);
		}
		
		assertThat(registry.receivedEvent()).isTrue();
		assertThat(registry.getEvent()).isInstanceOf(SessionExpiredEvent.class);
		
		assertThat(repository.getSession(sessionToSave.getId())).isNull();
	}
	
	@Test
	public void deletedSessionTest() throws InterruptedException {
		S sessionToSave = repository.createSession();
		
		repository.save(sessionToSave);
		
		assertThat(registry.receivedEvent()).isTrue();
		assertThat(registry.getEvent()).isInstanceOf(SessionCreatedEvent.class);
		registry.clear();
		
		repository.delete(sessionToSave.getId());
		
		assertThat(registry.receivedEvent()).isTrue();
		assertThat(registry.getEvent()).isInstanceOf(SessionDeletedEvent.class);
		
		assertThat(repository.getSession(sessionToSave.getId())).isNull();
	}
	
	
	@Configuration
	@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS_STR)
	static class HazelcastSessionConfig {
		
		@Bean
		public HazelcastInstance embeddedHazelcast() {
			Config hazelcastConfig = new Config();
			NetworkConfig netConfig = new NetworkConfig();
			netConfig.setPort(SocketUtils.findAvailableTcpPort());
			hazelcastConfig.setNetworkConfig(netConfig);
			return Hazelcast.newHazelcastInstance(hazelcastConfig);
		}
		
		@Bean
		public SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}
	}

}
