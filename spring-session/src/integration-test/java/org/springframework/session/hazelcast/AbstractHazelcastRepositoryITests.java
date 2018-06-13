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

package org.springframework.session.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.HazelcastInstanceProxy;
import org.junit.Assume;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.hazelcast.HazelcastSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for Hazelcast integration tests.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 */
public abstract class AbstractHazelcastRepositoryITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	@Autowired
	private HazelcastInstance hazelcastInstance;

	@Autowired
	private HazelcastSessionRepository repository;

	@Test
	public void createAndDestroySession() {
		HazelcastSession sessionToSave = this.repository.createSession();
		String sessionId = sessionToSave.getId();

		IMap<String, MapSession> hazelcastMap = this.hazelcastInstance
				.getMap("spring:session:sessions");

		assertThat(hazelcastMap.size()).isEqualTo(0);

		this.repository.save(sessionToSave);

		assertThat(hazelcastMap.size()).isEqualTo(1);
		assertThat(hazelcastMap.get(sessionId)).isEqualTo(sessionToSave);

		this.repository.delete(sessionId);

		assertThat(hazelcastMap.size()).isEqualTo(0);
	}

	@Test // gh-1106
	public void attemptToUpdateSessionAfterDelete() {
		HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();
		this.repository.save(session);
		session = this.repository.getSession(sessionId);
		session.setAttribute("attributeName", "attributeValue");
		this.repository.delete(sessionId);
		this.repository.save(session);

		assertThat(this.repository.getSession(sessionId)).isNull();
	}

	@Test
	public void createAndUpdateSession() {
		HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();

		this.repository.save(session);

		session = this.repository.getSession(sessionId);
		session.setAttribute("attributeName", "attributeValue");

		this.repository.save(session);

		assertThat(this.repository.getSession(sessionId)).isNotNull();
	}

	@Test
	public void createSessionWithSecurityContextAndFindById() {
		HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();

		Authentication authentication = new UsernamePasswordAuthenticationToken(
				"saves-" + System.currentTimeMillis(), "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		securityContext.setAuthentication(authentication);
		session.setAttribute(SPRING_SECURITY_CONTEXT, securityContext);

		this.repository.save(session);

		assertThat(this.repository.getSession(sessionId)).isNotNull();
	}

	@Test
	public void createSessionWithSecurityContextAndFindByPrincipal() {
		Assume.assumeTrue("Hazelcast runs in embedded server topology",
				this.hazelcastInstance instanceof HazelcastInstanceProxy);

		HazelcastSession session = this.repository.createSession();

		String username = "saves-" + System.currentTimeMillis();
		Authentication authentication = new UsernamePasswordAuthenticationToken(username,
				"password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		securityContext.setAuthentication(authentication);
		session.setAttribute(SPRING_SECURITY_CONTEXT, securityContext);

		this.repository.save(session);

		assertThat(this.repository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username))
				.isNotNull();
	}

}
