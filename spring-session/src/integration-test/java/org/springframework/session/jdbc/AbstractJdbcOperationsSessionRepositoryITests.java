/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.jdbc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for {@link JdbcOperationsSessionRepository} integration tests.
 *
 * @author Vedran Pavic
 * @since 1.2.0
 */
@WebAppConfiguration
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractJdbcOperationsSessionRepositoryITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private JdbcOperationsSessionRepository repository;

	private SecurityContext context;

	private SecurityContext changedContext;

	@Before
	public void setup() throws Exception {
		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(
				new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(),
						"na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));
	}

	@Test
	public void saves() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username,
				"password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, toSaveContext);
		toSave.setAttribute(INDEX_NAME, username);

		this.repository.save(toSave);

		Session session = this.repository.getSession(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.getAttribute(expectedAttributeName))
				.isEqualTo(toSave.getAttribute(expectedAttributeName));

		this.repository.delete(toSave.getId());

		assertThat(this.repository.getSession(toSave.getId())).isNull();
	}

	@Test
	@Transactional(readOnly = true)
	public void savesInReadOnlyTransaction() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();

		this.repository.save(toSave);
	}

	@Test
	public void putAllOnSingleAttrDoesNotRemoveOld() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute("a", "b");

		this.repository.save(toSave);
		toSave = this.repository.getSession(toSave.getId());

		toSave.setAttribute("1", "2");

		this.repository.save(toSave);
		toSave = this.repository.getSession(toSave.getId());

		Session session = this.repository.getSession(toSave.getId());
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.getAttribute("a")).isEqualTo("b");
		assertThat(session.getAttribute("1")).isEqualTo("2");

		this.repository.delete(toSave.getId());
	}

	@Test
	public void updateLastAccessedTime() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setLastAccessedTime(System.currentTimeMillis()
				- (MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 1000 + 1000));

		this.repository.save(toSave);

		long lastAccessedTime = System.currentTimeMillis();
		toSave.setLastAccessedTime(lastAccessedTime);
		this.repository.save(toSave);

		ExpiringSession session = this.repository.getSession(toSave.getId());

		assertThat(session).isNotNull();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessedTime);
	}

	@Test
	public void findByPrincipalName() throws Exception {
		String principalName = "findByPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.delete(toSave.getId());

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameExpireRemovesIndex() throws Exception {
		String principalName = "findByPrincipalNameExpireRemovesIndex"
				+ UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);
		toSave.setLastAccessedTime(System.currentTimeMillis()
				- (MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 1000 + 1000));

		this.repository.save(toSave);
		this.repository.cleanUpExpiredSessions();

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChange() throws Exception {
		String principalName = "findByPrincipalNameNoPrincipalNameChange"
				+ UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChangeReload() throws Exception {
		String principalName = "findByPrincipalNameNoPrincipalNameChangeReload"
				+ UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave = this.repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedPrincipalName() throws Exception {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, null);
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalName() throws Exception {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedPrincipalNameReload() throws Exception {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalNameReload() throws Exception {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.getSession(toSave.getId());

		getSession.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findBySecurityPrincipalName() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.delete(toSave.getId());

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findBySecurityPrincipalNameExpireRemovesIndex() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);
		toSave.setLastAccessedTime(System.currentTimeMillis()
				- (MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 1000 + 1000));

		this.repository.save(toSave);
		this.repository.cleanUpExpiredSessions();

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChange() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChangeReload()
			throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave = this.repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedSecurityPrincipalName() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, null);
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalName() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedSecurityPrincipalNameReload() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalNameReload() throws Exception {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.getSession(toSave.getId());

		getSession.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void cleanupInactiveSessionsUsingRepositoryDefinedInterval() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();

		this.repository.save(session);

		assertThat(this.repository.getSession(session.getId())).isNotNull();

		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.getSession(session.getId())).isNotNull();

		long now = System.currentTimeMillis();

		session.setLastAccessedTime(now - TimeUnit.MINUTES.toMillis(10));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.getSession(session.getId())).isNotNull();

		session.setLastAccessedTime(now - TimeUnit.MINUTES.toMillis(30));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.getSession(session.getId())).isNull();
	}

	// gh-580
	@Test
	public void cleanupInactiveSessionsUsingSessionDefinedInterval() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setMaxInactiveIntervalInSeconds((int) TimeUnit.MINUTES.toSeconds(45));

		this.repository.save(session);

		assertThat(this.repository.getSession(session.getId())).isNotNull();

		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.getSession(session.getId())).isNotNull();

		long now = System.currentTimeMillis();

		session.setLastAccessedTime(now - TimeUnit.MINUTES.toMillis(40));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.getSession(session.getId())).isNotNull();

		session.setLastAccessedTime(now - TimeUnit.MINUTES.toMillis(50));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.getSession(session.getId())).isNull();
	}

	private String getSecurityName() {
		return this.context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return this.changedContext.getAuthentication().getName();
	}

	@EnableJdbcHttpSession
	protected static class BaseConfig {

		@Bean
		public PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

	}

}
