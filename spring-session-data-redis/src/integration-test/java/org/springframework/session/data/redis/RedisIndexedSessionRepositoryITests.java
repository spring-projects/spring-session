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

package org.springframework.session.data.redis;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.SessionEventRegistry;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for {@link RedisIndexedSessionRepository}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 */
@SpringJUnitWebConfig
class RedisIndexedSessionRepositoryITests extends AbstractRedisITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private RedisIndexedSessionRepository repository;

	@Autowired
	private SessionEventRegistry registry;

	@SpringSessionRedisOperations
	private RedisOperations<Object, Object> redis;

	private SecurityContext context;

	private SecurityContext changedContext;

	@BeforeEach
	void setup() {
		if (this.registry != null) {
			this.registry.clear();
		}
		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));
	}

	@Test
	void saves() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		String usernameSessionKey = "RedisIndexedSessionRepositoryITests:index:" + INDEX_NAME + ":" + username;

		RedisSession toSave = this.repository.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, toSaveContext);
		toSave.setAttribute(INDEX_NAME, username);
		this.registry.clear();

		this.repository.save(toSave);

		assertThat(this.registry.receivedEvent(toSave.getId())).isTrue();
		assertThat(this.redis.boundSetOps(usernameSessionKey).members()).contains(toSave.getId());

		SessionCreatedEvent createdEvent = this.registry.getEvent(toSave.getId());
		Session session = createdEvent.getSession();

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
			.isEqualTo(toSave.getAttribute(expectedAttributeName));

		this.registry.clear();

		this.repository.deleteById(toSave.getId());

		assertThat(this.repository.findById(toSave.getId())).isNull();
		assertThat(this.registry.<SessionDestroyedEvent>getEvent(toSave.getId()))
			.isInstanceOf(SessionDestroyedEvent.class);
		assertThat(this.redis.boundSetOps(usernameSessionKey).members()).doesNotContain(toSave.getId());

		assertThat(this.registry.getEvent(toSave.getId()).getSession().<String>getAttribute(expectedAttributeName))
			.isEqualTo(expectedAttributeValue);
	}

	@Test
	void saveThenSaveSessionKeyAndShadowKeyWith5MinutesDifference() {
		RedisSession toSave = this.repository.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		this.repository.save(toSave);

		Long sessionKeyExpire = this.redis.getExpire("RedisIndexedSessionRepositoryITests:sessions:" + toSave.getId(),
				TimeUnit.SECONDS);
		Long shadowKeyExpire = this.redis
			.getExpire("RedisIndexedSessionRepositoryITests:sessions:expires:" + toSave.getId(), TimeUnit.SECONDS);
		long differenceInSeconds = sessionKeyExpire - shadowKeyExpire;
		assertThat(differenceInSeconds).isEqualTo(300);
	}

	@Test
	void putAllOnSingleAttrDoesNotRemoveOld() {
		RedisSession toSave = this.repository.createSession();
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
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.deleteById(toSave.getId());
		assertThat(this.registry.receivedEvent(toSave.getId())).isTrue();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	void findByPrincipalNameExpireRemovesIndex() {
		String principalName = "findByPrincipalNameExpireRemovesIndex" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		String body = "RedisIndexedSessionRepositoryITests:sessions:expires:" + toSave.getId();
		String channel = "__keyevent@0__:expired";
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));
		byte[] pattern = new byte[] {};
		this.repository.onMessage(message, pattern);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	void findByPrincipalNameNoPrincipalNameChange() {
		String principalName = "findByPrincipalNameNoPrincipalNameChange" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByPrincipalNameNoPrincipalNameChangeReload() {
		String principalName = "findByPrincipalNameNoPrincipalNameChangeReload" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedPrincipalName() {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, null);
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedPrincipalName() {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedPrincipalNameReload() {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		RedisSession getSession = this.repository.findById(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedPrincipalNameReload() {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		RedisSession getSession = this.repository.findById(toSave.getId());

		getSession.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findBySecurityPrincipalName() throws Exception {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		this.repository.deleteById(toSave.getId());
		assertThat(this.registry.receivedEvent(toSave.getId())).isTrue();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	void findBySecurityPrincipalNameExpireRemovesIndex() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		String body = "RedisIndexedSessionRepositoryITests:sessions:expires:" + toSave.getId();
		String channel = "__keyevent@0__:expired";
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));
		byte[] pattern = new byte[] {};
		this.repository.onMessage(message, pattern);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	void findByPrincipalNameNoSecurityPrincipalNameChange() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByPrincipalNameNoSecurityPrincipalNameChangeReload() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave = this.repository.findById(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedSecurityPrincipalName() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, null);
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedSecurityPrincipalName() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByDeletedSecurityPrincipalNameReload() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		RedisSession getSession = this.repository.findById(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getChangedSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByChangedSecurityPrincipalNameReload() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		RedisSession getSession = this.repository.findById(toSave.getId());

		getSession.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test // gh-1791
	void changeSessionIdWhenSessionExpiresThenRemovesAllPrincipalIndexIds() {
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);
		String usernameSessionKey = "RedisIndexedSessionRepositoryITests:index:" + INDEX_NAME + ":" + getSecurityName();

		RedisSession findById = this.repository.findById(toSave.getId());
		String originalFindById = findById.getId();

		assertThat(this.redis.boundSetOps(usernameSessionKey).members()).contains(originalFindById);

		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(findById);

		assertThat(this.redis.boundSetOps(usernameSessionKey).members()).contains(changeSessionId);

		String body = "RedisIndexedSessionRepositoryITests:sessions:expires:" + changeSessionId;
		String channel = "__keyevent@0__:expired";
		DefaultMessage message = new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));
		byte[] pattern = new byte[] {};
		this.repository.onMessage(message, pattern);

		assertThat(this.redis.boundSetOps(usernameSessionKey).members()).isEmpty();
	}

	@Test
	void changeSessionIdWhenPrincipalNameChangesThenNewPrincipalMapsToNewSessionId() {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		RedisSession findById = this.repository.findById(toSave.getId());
		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(findById);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(changeSessionId);
	}

	@Test // gh-1987
	void changeSessionIdWhenPrincipalNameChangesFromNullThenIndexShouldNotBeCreated() {
		String principalName = null;
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave);

		RedisSession findById = this.repository.findById(toSave.getId());
		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(findById);

		Map<String, RedisSession> findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(changeSessionId);
	}

	@Test
	void changeSessionIdWhenOnlyChangeId() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		RedisSession toSave = this.repository.createSession();
		toSave.setAttribute(attrName, attrValue);

		this.repository.save(toSave);

		RedisSession findById = this.repository.findById(toSave.getId());

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById);

		assertThat(this.repository.findById(originalFindById)).isNull();

		RedisSession findByChangeSessionId = this.repository.findById(changeSessionId);

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);
	}

	@Test
	void changeSessionIdWhenChangeTwice() {
		RedisSession toSave = this.repository.createSession();

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
	void changeSessionIdWhenSetAttributeOnChangedSession() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		RedisSession toSave = this.repository.createSession();

		this.repository.save(toSave);

		RedisSession findById = this.repository.findById(toSave.getId());

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById);

		assertThat(this.repository.findById(originalFindById)).isNull();

		RedisSession findByChangeSessionId = this.repository.findById(changeSessionId);

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);
	}

	@Test
	void changeSessionIdWhenHasNotSaved() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		RedisSession toSave = this.repository.createSession();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave);

		assertThat(this.repository.findById(toSave.getId())).isNotNull();
		assertThat(this.repository.findById(originalId)).isNull();
	}

	// gh-962
	@Test
	void changeSessionIdSaveTwice() {
		RedisSession toSave = this.repository.createSession();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave);
		this.repository.save(toSave);

		assertThat(this.repository.findById(toSave.getId())).isNotNull();
		assertThat(this.repository.findById(originalId)).isNull();
	}

	// gh-1137
	@Test
	void changeSessionIdWhenSessionIsDeleted() {
		RedisSession toSave = this.repository.createSession();
		String sessionId = toSave.getId();
		this.repository.save(toSave);

		this.repository.deleteById(sessionId);

		toSave.changeSessionId();
		this.repository.save(toSave);

		assertThat(this.repository.findById(toSave.getId())).isNull();
		assertThat(this.repository.findById(sessionId)).isNull();
	}

	@Test // gh-1270
	void changeSessionIdSaveConcurrently() {
		RedisSession toSave = this.repository.createSession();
		String originalId = toSave.getId();
		this.repository.save(toSave);

		RedisSession copy1 = this.repository.findById(originalId);
		RedisSession copy2 = this.repository.findById(originalId);

		copy1.changeSessionId();
		this.repository.save(copy1);
		copy2.changeSessionId();
		this.repository.save(copy2);

		assertThat(this.repository.findById(originalId)).isNull();
		assertThat(this.repository.findById(copy1.getId())).isNotNull();
		assertThat(this.repository.findById(copy2.getId())).isNull();
	}

	// gh-1743
	@Test
	void saveChangeSessionIdWhenFailedRenameOperationExceptionThenIgnoreError() {
		RedisSession toSave = this.repository.createSession();
		String sessionId = toSave.getId();

		this.repository.save(toSave);
		RedisSession session = this.repository.findById(sessionId);
		this.repository.deleteById(sessionId);
		session.changeSessionId();

		assertThatNoException().isThrownBy(() -> this.repository.save(session));
	}

	private String getSecurityName() {
		return this.context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return this.changedContext.getAuthentication().getName();
	}

	@Configuration
	@EnableRedisIndexedHttpSession(redisNamespace = "RedisIndexedSessionRepositoryITests")
	static class Config extends BaseConfig {

		@Bean
		SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}

	}

}
