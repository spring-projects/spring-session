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
				.getMap(HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME);

		assertThat(hazelcastMap.size()).isEqualTo(0);

		this.repository.save(sessionToSave);

		assertThat(hazelcastMap.size()).isEqualTo(1);
		assertThat(hazelcastMap.get(sessionId)).isEqualTo(sessionToSave);

		this.repository.deleteById(sessionId);

		assertThat(hazelcastMap.size()).isEqualTo(0);
	}

	@Test
	public void changeSessionIdWhenOnlyChangeId() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		HazelcastSession toSave = this.repository.createSession();
		toSave.setAttribute(attrName, attrValue);

		this.repository.save(toSave);

		HazelcastSession findById = this.repository.findById(toSave.getId());

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById);

		assertThat(this.repository.findById(originalFindById)).isNull();

		HazelcastSession findByChangeSessionId = this.repository
				.findById(changeSessionId);

		assertThat(findByChangeSessionId.<String>getAttribute(attrName))
				.isEqualTo(attrValue);

		this.repository.deleteById(changeSessionId);
	}

	@Test
	public void changeSessionIdWhenChangeTwice() {
		HazelcastSession toSave = this.repository.createSession();

		this.repository.save(toSave);

		String originalId = toSave.getId();
		String changeId1 = toSave.changeSessionId();
		String changeId2 = toSave.changeSessionId();

		this.repository.save(toSave);

		assertThat(this.repository.findById(originalId)).isNull();
		assertThat(this.repository.findById(changeId1)).isNull();
		assertThat(this.repository.findById(changeId2)).isNotNull();

		this.repository.deleteById(changeId2);
	}

	@Test
	public void changeSessionIdWhenSetAttributeOnChangedSession() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		HazelcastSession toSave = this.repository.createSession();

		this.repository.save(toSave);

		HazelcastSession findById = this.repository.findById(toSave.getId());

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById);

		assertThat(this.repository.findById(originalFindById)).isNull();

		HazelcastSession findByChangeSessionId = this.repository
				.findById(changeSessionId);

		assertThat(findByChangeSessionId.<String>getAttribute(attrName))
				.isEqualTo(attrValue);

		this.repository.deleteById(changeSessionId);
	}

	@Test
	public void changeSessionIdWhenHasNotSaved() {
		HazelcastSession toSave = this.repository.createSession();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave);

		assertThat(this.repository.findById(toSave.getId())).isNotNull();
		assertThat(this.repository.findById(originalId)).isNull();

		this.repository.deleteById(toSave.getId());
	}

	@Test // gh-1076
	public void attemptToUpdateSessionAfterDelete() {
		HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();
		this.repository.save(session);
		session = this.repository.findById(sessionId);
		session.setAttribute("attributeName", "attributeValue");
		this.repository.deleteById(sessionId);
		this.repository.save(session);

		assertThat(this.repository.findById(sessionId)).isNull();
	}

	@Test
	public void createAndUpdateSession() {
		HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();

		this.repository.save(session);

		session = this.repository.findById(sessionId);
		session.setAttribute("attributeName", "attributeValue");

		this.repository.save(session);

		assertThat(this.repository.findById(sessionId)).isNotNull();
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

		assertThat(this.repository.findById(sessionId)).isNotNull();
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
