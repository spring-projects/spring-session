/*
 * Copyright 2002-2016 the original author or authors.
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
package org.springframework.session.data.mongo;

import com.mongodb.MongoClient;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.AbstractITests;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.test.context.ContextConfiguration;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jakub Kubrynski
 */
@ContextConfiguration
public class MongoRepositoryITests extends AbstractITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	protected MongoOperationsSessionRepository repository;

	@Test
	public void saves() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		MongoExpiringSession toSave = repository.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, toSaveContext);
		toSave.setAttribute(INDEX_NAME, username);

		repository.save(toSave);

		Session session = repository.getSession(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.getAttribute(expectedAttributeName)).isEqualTo(toSave.getAttribute(expectedAttributeName));

		repository.delete(toSave.getId());

		String id = toSave.getId();
		assertThat(repository.getSession(id)).isNull();
	}

	@Test
	public void putAllOnSingleAttrDoesNotRemoveOld() {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute("a", "b");

		repository.save(toSave);
		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("1", "2");

		repository.save(toSave);
		toSave = repository.getSession(toSave.getId());

		Session session = repository.getSession(toSave.getId());
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.getAttribute("a")).isEqualTo("b");
		assertThat(session.getAttribute("1")).isEqualTo("2");

		repository.delete(toSave.getId());
	}

	@Test
	public void findByPrincipalName() throws Exception {
		String principalName = "findByPrincipalName" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		repository.delete(toSave.getId());

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChange() throws Exception {
		String principalName = "findByPrincipalNameNoPrincipalNameChange" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChangeReload() throws Exception {
		String principalName = "findByPrincipalNameNoPrincipalNameChangeReload" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedPrincipalName() throws Exception {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, null);
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalName() throws Exception {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, principalNameChanged);
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedPrincipalNameReload() throws Exception {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		MongoExpiringSession getSession = repository.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		repository.save(getSession);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalNameReload() throws Exception {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		MongoExpiringSession getSession = repository.getSession(toSave.getId());

		getSession.setAttribute(INDEX_NAME, principalNameChanged);
		repository.save(getSession);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findBySecurityPrincipalName() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		repository.delete(toSave.getId());

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChange() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChangeReload() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedSecurityPrincipalName() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, null);
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalName() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, changedContext);
		repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedSecurityPrincipalNameReload() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		MongoExpiringSession getSession = repository.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		repository.save(getSession);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getChangedSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalNameReload() throws Exception {
		MongoExpiringSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		MongoExpiringSession getSession = repository.getSession(toSave.getId());

		getSession.setAttribute(SPRING_SECURITY_CONTEXT, changedContext);
		repository.save(getSession);

		Map<String, MongoExpiringSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void loadExpiredSession() throws Exception {
		//given
		MongoExpiringSession expiredSession = repository.createSession();
		long thirtyOneMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(31);
		expiredSession.setLastAccessedTime(thirtyOneMinutesAgo);
		repository.save(expiredSession);

		//then
		MongoExpiringSession expiredSessionFromDb = repository.getSession(expiredSession.getId());
		assertThat(expiredSessionFromDb).isNull();
	}

	private String getSecurityName() {
		return context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return changedContext.getAuthentication().getName();
	}

	@Configuration
	@EnableMongoHttpSession
	static class Config {

		@Bean
		public MongoOperations mongoOperations() throws UnknownHostException {
			return new MongoTemplate(new MongoClient(), "test");
		}

	}
}