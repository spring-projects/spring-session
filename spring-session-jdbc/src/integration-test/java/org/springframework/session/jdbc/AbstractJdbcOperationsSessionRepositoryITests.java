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

package org.springframework.session.jdbc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for {@link JdbcOperationsSessionRepository} integration tests.
 *
 * @author Vedran Pavic
 */
public abstract class AbstractJdbcOperationsSessionRepositoryITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private JdbcOperationsSessionRepository repository;

	private SecurityContext context;

	private SecurityContext changedContext;

	@Before
	public void setUp() {
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
	public void saveWhenNoAttributesThenCanBeFound() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();

		this.repository.save(toSave);
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(toSave.getId());

		assertThat(session).isNotNull();
		assertThat(session.isChanged()).isFalse();
		assertThat(session.getDelta()).isEmpty();
	}

	@Test
	public void saves() {
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

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.isChanged()).isFalse();
		assertThat(session.getDelta()).isEmpty();
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
				.isEqualTo(toSave.getAttribute(expectedAttributeName));

		this.repository.deleteById(toSave.getId());

		assertThat(this.repository.findById(toSave.getId())).isNull();
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
		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("1", "2");

		this.repository.save(toSave);
		toSave = this.repository.findById(toSave.getId());

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(toSave.getId());
		assertThat(session.isChanged()).isFalse();
		assertThat(session.getDelta()).isEmpty();
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.<String>getAttribute("a")).isEqualTo("b");
		assertThat(session.<String>getAttribute("1")).isEqualTo("2");

		this.repository.deleteById(toSave.getId());
	}

	@Test
	public void updateLastAccessedTime() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setLastAccessedTime(Instant.now()
				.minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));

		this.repository.save(toSave);

		Instant lastAccessedTime = Instant.now();
		toSave.setLastAccessedTime(lastAccessedTime);
		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(toSave.getId());

		assertThat(session).isNotNull();
		assertThat(session.isChanged()).isFalse();
		assertThat(session.getDelta()).isEmpty();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(lastAccessedTime.truncatedTo(ChronoUnit.MILLIS));
	}

	@Test
	public void findByPrincipalName() {
		String principalName = "findByPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.deleteById(toSave.getId());

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameExpireRemovesIndex() {
		String principalName = "findByPrincipalNameExpireRemovesIndex"
				+ UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);
		toSave.setLastAccessedTime(Instant.now()
				.minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));

		this.repository.save(toSave);
		this.repository.cleanUpExpiredSessions();

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChange() {
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
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChangeReload() {
		String principalName = "findByPrincipalNameNoPrincipalNameChangeReload"
				+ UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findByDeletedPrincipalName() {
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
	public void findByChangedPrincipalName() {
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
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findByDeletedPrincipalNameReload() {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.findById(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalNameReload() {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.findById(toSave.getId());

		getSession.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findBySecurityPrincipalName() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.deleteById(toSave.getId());

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findBySecurityPrincipalNameExpireRemovesIndex() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);
		toSave.setLastAccessedTime(Instant.now()
				.minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));

		this.repository.save(toSave);
		this.repository.cleanUpExpiredSessions();

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChange() {
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
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChangeReload() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findByDeletedSecurityPrincipalName() {
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
	public void findByChangedSecurityPrincipalName() {
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
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void findByDeletedSecurityPrincipalNameReload() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.findById(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalNameReload() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession getSession = this.repository
				.findById(toSave.getId());

		getSession.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(getSession);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
		findByPrincipalName.values().forEach((session) -> {
			assertThat(session.isChanged()).isFalse();
			assertThat(session.getDelta()).isEmpty();
		});
	}

	@Test
	public void cleanupInactiveSessionsUsingRepositoryDefinedInterval() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();

		this.repository.save(session);

		assertThat(this.repository.findById(session.getId())).isNotNull();

		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.findById(session.getId())).isNotNull();

		Instant now = Instant.now();

		session.setLastAccessedTime(now.minus(10, ChronoUnit.MINUTES));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.findById(session.getId())).isNotNull();

		session.setLastAccessedTime(now.minus(30, ChronoUnit.MINUTES));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.findById(session.getId())).isNull();
	}

	// gh-580
	@Test
	public void cleanupInactiveSessionsUsingSessionDefinedInterval() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setMaxInactiveInterval(Duration.ofMinutes(45));

		this.repository.save(session);

		assertThat(this.repository.findById(session.getId())).isNotNull();

		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.findById(session.getId())).isNotNull();

		Instant now = Instant.now();

		session.setLastAccessedTime(now.minus(40, ChronoUnit.MINUTES));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.findById(session.getId())).isNotNull();

		session.setLastAccessedTime(now.minus(50, ChronoUnit.MINUTES));
		this.repository.save(session);
		this.repository.cleanUpExpiredSessions();

		assertThat(this.repository.findById(session.getId())).isNull();
	}

	@Test
	public void changeSessionIdWhenOnlyChangeId() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		toSave.setAttribute(attrName, attrValue);

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession findById = this.repository
				.findById(toSave.getId());

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById);

		assertThat(this.repository.findById(originalFindById)).isNull();

		JdbcOperationsSessionRepository.JdbcSession findByChangeSessionId = this.repository
				.findById(changeSessionId);

		assertThat(findByChangeSessionId.isChanged()).isFalse();
		assertThat(findByChangeSessionId.getDelta()).isEmpty();
		assertThat(findByChangeSessionId.<String>getAttribute(attrName))
				.isEqualTo(attrValue);
	}

	@Test
	public void changeSessionIdWhenChangeTwice() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();

		this.repository.save(toSave);

		String originalId = toSave.getId();
		String changeId1 = toSave.changeSessionId();
		String changeId2 = toSave.changeSessionId();

		this.repository.save(toSave);

		assertThat(this.repository.findById(originalId)).isNull();
		assertThat(this.repository.findById(changeId1)).isNull();
		assertThat(this.repository.findById(changeId2)).isNotNull();
	}

	@Test
	public void changeSessionIdWhenSetAttributeOnChangedSession() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();

		this.repository.save(toSave);

		JdbcOperationsSessionRepository.JdbcSession findById = this.repository
				.findById(toSave.getId());

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById);

		assertThat(this.repository.findById(originalFindById)).isNull();

		JdbcOperationsSessionRepository.JdbcSession findByChangeSessionId = this.repository
				.findById(changeSessionId);

		assertThat(findByChangeSessionId.isChanged()).isFalse();
		assertThat(findByChangeSessionId.getDelta()).isEmpty();
		assertThat(findByChangeSessionId.<String>getAttribute(attrName))
				.isEqualTo(attrValue);
	}

	@Test
	public void changeSessionIdWhenHasNotSaved() {
		JdbcOperationsSessionRepository.JdbcSession toSave = this.repository
				.createSession();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave);

		assertThat(this.repository.findById(toSave.getId())).isNotNull();
		assertThat(this.repository.findById(originalId)).isNull();
	}

	@Test // gh-1070
	public void saveUpdatedAddAndModifyAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		session.setAttribute("testName", "testValue1");
		session.setAttribute("testName", "testValue2");
		this.repository.save(session);
		session = this.repository.findById(session.getId());

		assertThat(session.<String>getAttribute("testName")).isEqualTo("testValue2");
	}

	@Test // gh-1070
	public void saveUpdatedAddAndRemoveAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		session.setAttribute("testName", "testValue");
		session.removeAttribute("testName");
		this.repository.save(session);
		session = this.repository.findById(session.getId());

		assertThat(session.<String>getAttribute("testName")).isNull();
	}

	@Test // gh-1070
	public void saveUpdatedModifyAndRemoveAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setAttribute("testName", "testValue1");
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		session.setAttribute("testName", "testValue2");
		session.removeAttribute("testName");
		this.repository.save(session);
		session = this.repository.findById(session.getId());

		assertThat(session.<String>getAttribute("testName")).isNull();
	}

	@Test // gh-1070
	public void saveUpdatedRemoveAndAddAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setAttribute("testName", "testValue1");
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		session.removeAttribute("testName");
		session.setAttribute("testName", "testValue2");
		this.repository.save(session);
		session = this.repository.findById(session.getId());

		assertThat(session.<String>getAttribute("testName")).isEqualTo("testValue2");
	}

	@Test // gh-1031
	public void saveDeleted() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		this.repository.deleteById(session.getId());
		session.setLastAccessedTime(Instant.now());
		this.repository.save(session);

		assertThat(this.repository.findById(session.getId())).isNull();
	}

	@Test // gh-1031
	public void saveDeletedAddAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		this.repository.deleteById(session.getId());
		session.setLastAccessedTime(Instant.now());
		session.setAttribute("testName", "testValue1");
		this.repository.save(session);

		assertThat(this.repository.findById(session.getId())).isNull();
	}

	@Test // gh-1133
	public void sessionFromStoreResolvesAttributesLazily() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");
		this.repository.save(session);
		session = this.repository.findById(session.getId());
		MapSession delegate = (MapSession) ReflectionTestUtils.getField(session,
				"delegate");

		assertThat((String) session.getAttribute("attribute1")).isEqualTo("value1");
		assertThat(delegate).isNotNull();
		assertThat(ReflectionTestUtils
				.getField((Supplier) delegate.getAttribute("attribute1"), "value"))
						.isEqualTo("value1");
		assertThat(ReflectionTestUtils
				.getField((Supplier) delegate.getAttribute("attribute2"), "value"))
						.isNull();
		assertThat((String) session.getAttribute("attribute2")).isEqualTo("value2");
		assertThat(ReflectionTestUtils
				.getField((Supplier) delegate.getAttribute("attribute2"), "value"))
						.isEqualTo("value2");
	}

	@Test // gh-1203
	public void saveWithLargeAttribute() {
		String attributeName = "largeAttribute";
		int arraySize = 4000;

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setAttribute(attributeName, new byte[arraySize]);
		this.repository.save(session);
		session = this.repository.findById(session.getId());

		assertThat(session).isNotNull();
		assertThat((byte[]) session.getAttribute(attributeName)).hasSize(arraySize);
	}

	private String getSecurityName() {
		return this.context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return this.changedContext.getAuthentication().getName();
	}

	@EnableJdbcHttpSession
	static class BaseConfig {

		@Bean
		public PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

	}

}
