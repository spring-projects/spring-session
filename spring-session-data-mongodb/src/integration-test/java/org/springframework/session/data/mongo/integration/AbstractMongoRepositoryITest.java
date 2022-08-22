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

package org.springframework.session.data.mongo.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;
import org.springframework.session.data.mongo.MongoSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for {@link MongoIndexedSessionRepository} tests.
 *
 * @author Jakub Kubrynski
 * @author Vedran Pavic
 * @author Greg Turnquist
 */
public abstract class AbstractMongoRepositoryITest extends AbstractITest {

	protected static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	protected static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	protected MongoIndexedSessionRepository repository;

	@Test
	void saves() {

		String username = "saves-" + System.currentTimeMillis();

		MongoSession toSave = this.repository.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, toSaveContext);
		toSave.setAttribute(INDEX_NAME, username);

		this.repository.save(toSave);

		Session session = this.repository.findById(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
				.isEqualTo(toSave.getAttribute(expectedAttributeName));

		this.repository.deleteById(toSave.getId());

		String id = toSave.getId();
		assertThat(this.repository.findById(id)).isNull();
	}

	@Test
	void putAllOnSingleAttrDoesNotRemoveOld() {

		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute("a", "b");

		this.repository.save(toSave);
		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("1", "2");

		this.repository.save(toSave);
		toSave = this.repository.findById(toSave.getId());

		Session session = this.repository.findById(toSave.getId());
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.<String>getAttribute("a")).isEqualTo("b");
		assertThat(session.<String>getAttribute("1")).isEqualTo("2");

		this.repository.deleteById(toSave.getId());
	}

	@Test
	void findByPrincipalName() throws Exception {

		String principalName = "findByPrincipalName" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.deleteById(toSave.getId());

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	void nonExistentSessionShouldNotBreakMongo() {
		this.repository.deleteById("doesn't exist");
	}

	@Test
	void findByPrincipalNameNoPrincipalNameChange() throws Exception {

		String principalName = "findByPrincipalNameNoPrincipalNameChange" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByPrincipalNameNoPrincipalNameChangeReload() throws Exception {

		String principalName = "findByPrincipalNameNoPrincipalNameChangeReload" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedPrincipalName() throws Exception {

		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, null);
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedPrincipalName() throws Exception {

		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedPrincipalNameReload() throws Exception {

		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		MongoSession getSession = this.repository.findById(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedPrincipalNameReload() throws Exception {

		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		MongoSession getSession = this.repository.findById(toSave.getId());

		getSession.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(getSession);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findBySecurityPrincipalName() throws Exception {

		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.deleteById(toSave.getId());

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	void findByPrincipalNameNoSecurityPrincipalNameChange() throws Exception {

		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedSecurityPrincipalName() throws Exception {

		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, null);
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedSecurityPrincipalName() throws Exception {

		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(toSave);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByChangedSecurityPrincipalNameReload() throws Exception {

		MongoSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		MongoSession getSession = this.repository.findById(toSave.getId());

		getSession.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(getSession);

		Map<String, MongoSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void loadExpiredSession() throws Exception {

		// given
		MongoSession expiredSession = this.repository.createSession();
		Instant thirtyOneMinutesAgo = Instant.ofEpochMilli(System.currentTimeMillis()).minus(Duration.ofMinutes(31));
		expiredSession.setLastAccessedTime(thirtyOneMinutesAgo);
		this.repository.save(expiredSession);

		// then
		MongoSession expiredSessionFromDb = this.repository.findById(expiredSession.getId());
		assertThat(expiredSessionFromDb).isNull();
	}

	protected String getSecurityName() {
		return this.context.getAuthentication().getName();
	}

	protected String getChangedSecurityName() {
		return this.changedContext.getAuthentication().getName();
	}

	protected static class BaseConfig {

		private static final String DOCKER_IMAGE = "mongo:5.0.11";

		@Bean
		public MongoDBContainer mongoDbContainer() {
			MongoDBContainer mongoDbContainer = new MongoDBContainer(DOCKER_IMAGE);
			mongoDbContainer.start();
			return mongoDbContainer;
		}

		@Bean
		public MongoOperations mongoOperations(MongoDBContainer mongoContainer) {

			MongoClient mongo = MongoClients.create(
					"mongodb://" + mongoContainer.getContainerIpAddress() + ":" + mongoContainer.getFirstMappedPort());
			return new MongoTemplate(mongo, "test");
		}

	}

}
