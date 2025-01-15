/*
 * Copyright 2014-2023 the original author or authors.
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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.data.SessionEventRegistry;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
@SuppressWarnings({ "ConstantConditions" })
class ReactiveRedisIndexedSessionRepositoryITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = ReactiveRedisIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private ReactiveRedisIndexedSessionRepository repository;

	@Autowired
	private SessionEventRegistry eventRegistry;

	@SpringSessionRedisOperations
	private ReactiveRedisOperations<Object, Object> redis;

	private SecurityContext context;

	private SecurityContext changedContext;

	@BeforeEach
	void setup() {
		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));
	}

	@Test
	void findByIdWhenSavedThenFound() {
		RedisSession session = this.repository.createSession().block();
		session.setAttribute("foo", "bar");
		this.repository.save(session).block();
		RedisSession savedSession = this.repository.findById(session.getId()).block();
		assertThat(savedSession).isNotNull();
		assertThat(savedSession.getId()).isEqualTo(session.getId());
		assertThat(savedSession.<String>getAttribute("foo")).isEqualTo("bar");
	}

	@Test
	void saveWhenHasSecurityContextAttributeThenPrincipalIndexKeySaved() {
		RedisSession session = this.repository.createSession().block();
		session.setAttribute("foo", "bar");

		String username = "user";
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		session.setAttribute(SPRING_SECURITY_CONTEXT, context);
		this.repository.save(session).block();

		String usernameSessionKey = "spring:session:sessions:index:"
				+ ReactiveRedisIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":" + username;
		Boolean sessionExistsOnPrincipalKey = this.redis.opsForSet()
			.isMember(usernameSessionKey, session.getId())
			.block();
		assertThat(sessionExistsOnPrincipalKey).isTrue();
	}

	@Test
	void saveWhenSuccessThenSessionCreatedEvent() throws InterruptedException {
		RedisSession session = this.repository.createSession().block();
		session.setAttribute("foo", "bar");

		this.repository.save(session).block();

		SessionCreatedEvent event = this.eventRegistry.getEvent(session.getId());
		assertThat(event).isNotNull();
		RedisSession eventSession = event.getSession();
		compareSessions(session, eventSession);
	}

	@Test
	void findByPrincipalNameWhenExistsThenReturn() {
		RedisSession session = this.repository.createSession().block();
		String principalName = "principal";
		session.setAttribute(ReactiveRedisIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);

		this.repository.save(session).block();

		Map<String, RedisSession> principalSessions = this.repository
			.findByIndexNameAndIndexValue(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
					principalName)
			.block();

		assertThat(principalSessions).hasSize(1);
		assertThat(principalSessions.keySet()).containsOnly(session.getId());

		this.repository.deleteById(session.getId()).block();

		principalSessions = this.repository
			.findByIndexNameAndIndexValue(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
					principalName)
			.block();
		assertThat(principalSessions).isEmpty();
	}

	@Test
	void findByPrincipalNameWhenExpireKeyEventThenRemovesIndexAndSessionExpiredEvent() {
		String principalName = "findByPrincipalNameExpireRemovesIndex" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave).block();

		this.eventRegistry.clear();
		String key = "spring:session:sessions:expires:" + toSave.getId();
		assertThat(this.redis.expire(key, Duration.ofSeconds(1)).block()).isTrue();

		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			SessionExpiredEvent event = this.eventRegistry.getEvent(toSave.getId());
			RedisSession eventSession = event.getSession();
			Map<String, RedisSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
				.block();
			assertThat(findByPrincipalName).hasSize(0);
			assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
			assertThat(event).isNotNull();
			compareSessions(toSave, eventSession);
		});
	}

	private static void compareSessions(RedisSession session1, RedisSession session2) {
		assertThat(session2.getCreationTime().truncatedTo(ChronoUnit.SECONDS))
			.isEqualTo(session1.getCreationTime().truncatedTo(ChronoUnit.SECONDS));
		assertThat(session2.getMaxInactiveInterval().truncatedTo(ChronoUnit.SECONDS))
			.isEqualTo(session1.getMaxInactiveInterval().truncatedTo(ChronoUnit.SECONDS));
		assertThat(session2.getId()).isEqualTo(session1.getId());
		assertThat(session2.getAttributeNames()).isEqualTo(session1.getAttributeNames());
	}

	@Test
	void findByPrincipalNameWhenDeletedKeyEventThenRemovesIndex() {
		String principalName = "findByPrincipalNameExpireRemovesIndex" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave).block();

		String key = "spring:session:sessions:expires:" + toSave.getId();
		assertThat(this.redis.delete(key).block()).isEqualTo(1);

		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			Map<String, RedisSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
				.block();
			assertThat(findByPrincipalName).hasSize(0);
			assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
			SessionDeletedEvent event = this.eventRegistry.getEvent(toSave.getId());
			assertThat(event).isNotNull();
			RedisSession eventSession = event.getSession();
			compareSessions(toSave, eventSession);
		});
	}

	@Test
	void findByPrincipalNameWhenNoPrincipalNameChangeThenKeepIndex() {
		String principalName = "findByPrincipalNameNoPrincipalNameChange" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave).block();

		toSave.setAttribute("other", "value");
		this.repository.save(toSave).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByPrincipalNameWhenNoPrincipalNameChangeAndFindByIdThenKeepIndex() {
		String principalName = "findByPrincipalNameNoPrincipalNameChange" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave).block();
		toSave = this.repository.findById(toSave.getId()).block();

		toSave.setAttribute("other", "value");
		this.repository.save(toSave).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByPrincipalNameWhenDeletedPrincipalAttributeThenEmpty() {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(INDEX_NAME, principalName);

		this.repository.save(toSave).block();

		toSave.removeAttribute(INDEX_NAME);
		this.repository.save(toSave).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByPrincipalNameWhenDeletedPrincipalAttributeAndFindByIdThenEmpty() {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(INDEX_NAME, principalName);

		this.repository.save(session).block();
		session = this.repository.findById(session.getId()).block();

		session.removeAttribute(INDEX_NAME);
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByPrincipalNameWhenChangedSecurityContextAttributeThenIndexMovedToNewPrincipal() {
		String principalName = this.context.getAuthentication().getName();
		String principalNameChanged = this.changedContext.getAuthentication().getName();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();

		session.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged).block();
		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(session.getId());
	}

	@Test
	void findByPrincipalNameWhenChangedSecurityContextAttributeAndFindByIdThenIndexMovedToNewPrincipal() {
		String principalName = this.context.getAuthentication().getName();
		String principalNameChanged = this.changedContext.getAuthentication().getName();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();
		session = this.repository.findById(session.getId()).block();

		session.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged).block();
		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(session.getId());
	}

	@Test
	void findByPrincipalNameWhenNoSecurityContextChangeThenKeepIndex() {
		String principalName = this.context.getAuthentication().getName();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();

		session.setAttribute("other", "value");
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(session.getId());
	}

	@Test
	void findByPrincipalNameWhenNoSecurityContextChangeAndFindByIdThenKeepIndex() {
		String principalName = this.context.getAuthentication().getName();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave).block();
		toSave = this.repository.findById(toSave.getId()).block();

		toSave.setAttribute("other", "value");
		this.repository.save(toSave).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	void findByPrincipalNameWhenDeletedSecurityContextAttributeThenEmpty() {
		String principalName = this.context.getAuthentication().getName();
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave).block();

		toSave.removeAttribute(SPRING_SECURITY_CONTEXT);
		this.repository.save(toSave).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByPrincipalNameWhenDeletedSecurityContextAttributeAndFindByIdThenEmpty() {
		String principalName = this.context.getAuthentication().getName();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();
		session = this.repository.findById(session.getId()).block();

		session.removeAttribute(SPRING_SECURITY_CONTEXT);
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)

			.block();

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	void findByPrincipalNameWhenChangedPrincipalAttributeThenEmpty() {
		String principalName = this.context.getAuthentication().getName();
		String principalNameChanged = this.changedContext.getAuthentication().getName();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();

		session.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged).block();
		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(session.getId());
	}

	@Test
	void findByPrincipalNameWhenChangedPrincipalAttributeAndFindByIdThenEmpty() {
		String principalName = this.context.getAuthentication().getName();
		String principalNameChanged = this.changedContext.getAuthentication().getName();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();
		session = this.repository.findById(session.getId()).block();

		session.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		this.repository.save(session).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged).block();
		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(session.getId());
	}

	// gh-1791
	@Test
	void changeSessionIdWhenSessionExpiredThenRemovesAllPrincipalIndex() {
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();
		String usernameSessionKey = "spring:session:sessions:index:" + INDEX_NAME + ":" + getSecurityName();

		RedisSession findById = this.repository.findById(session.getId()).block();
		String originalFindById = findById.getId();

		assertThat(this.redis.opsForSet().members(usernameSessionKey).collectList().block()).contains(originalFindById);

		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(findById).block();

		assertThat(this.redis.opsForSet().members(usernameSessionKey).collectList().block()).contains(changeSessionId);

		String key = "spring:session:sessions:expires:" + changeSessionId;
		assertThat(this.redis.expire(key, Duration.ofSeconds(1)).block()).isTrue(); // expire
																					// the
																					// key

		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> assertThat(this.redis.opsForSet().members(usernameSessionKey).collectList().block())
				.isEmpty());
	}

	@Test
	void changeSessionIdWhenSessionDeletedThenRemovesAllPrincipalIndex() {
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(session).block();
		String usernameSessionKey = "spring:session:sessions:index:" + INDEX_NAME + ":" + getSecurityName();

		RedisSession findById = this.repository.findById(session.getId()).block();
		String originalFindById = findById.getId();

		assertThat(this.redis.opsForSet().members(usernameSessionKey).collectList().block()).contains(originalFindById);

		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(findById).block();

		assertThat(this.redis.opsForSet().members(usernameSessionKey).collectList().block()).contains(changeSessionId);

		String key = "spring:session:sessions:expires:" + changeSessionId;
		assertThat(this.redis.delete(key).block()).isEqualTo(1);

		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> assertThat(this.redis.opsForSet().members(usernameSessionKey).collectList().block())
				.isEmpty());
	}

	@Test
	void changeSessionIdWhenPrincipalNameChangesThenNewPrincipalMapsToNewSessionId() {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(INDEX_NAME, principalName);

		this.repository.save(session).block();

		RedisSession findById = this.repository.findById(session.getId()).block();
		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(findById).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)

			.block();
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged).block();

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(changeSessionId);
	}

	// gh-1987
	@Test
	void changeSessionIdWhenPrincipalNameChangesFromNullThenIndexShouldNotBeCreated() {
		String principalName = null;
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(INDEX_NAME, principalName);

		this.repository.save(session).block();

		RedisSession findById = this.repository.findById(session.getId()).block();
		String changeSessionId = findById.changeSessionId();
		findById.setAttribute(INDEX_NAME, principalNameChanged);
		this.repository.save(findById).block();

		Map<String, RedisSession> findByPrincipalName = this.repository
			.findByIndexNameAndIndexValue(INDEX_NAME, principalName)
			.block();
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = this.repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged).block();

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(changeSessionId);
	}

	@Test
	void changeSessionIdWhenOnlyChangeId() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		RedisSession session = this.repository.createSession().block();
		session.setAttribute(attrName, attrValue);

		this.repository.save(session).block();

		RedisSession findById = this.repository.findById(session.getId()).block();

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		RedisSession findByChangeSessionId = this.repository.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);
	}

	@Test
	void changeSessionIdWhenChangeTwice() {
		RedisSession session = this.repository.createSession().block();

		this.repository.save(session).block();

		String originalId = session.getId();
		String changeId1 = session.changeSessionId();
		String changeId2 = session.changeSessionId();

		this.repository.save(session).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(changeId1).block()).isNull();
		assertThat(this.repository.findById(changeId2).block()).isNotNull();
	}

	@Test
	void changeSessionIdWhenSetAttributeOnChangedSession() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		RedisSession session = this.repository.createSession().block();

		this.repository.save(session).block();

		RedisSession findById = this.repository.findById(session.getId()).block();

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		RedisSession findByChangeSessionId = this.repository.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);
	}

	@Test
	void changeSessionIdWhenHasNotSaved() {
		RedisSession session = this.repository.createSession().block();
		String originalId = session.getId();
		session.changeSessionId();

		this.repository.save(session).block();

		assertThat(this.repository.findById(session.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();
	}

	// gh-962
	@Test
	void changeSessionIdSaveTwice() {
		RedisSession toSave = this.repository.createSession().block();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave).block();
		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();
	}

	// gh-1137
	@Test
	void changeSessionIdWhenSessionIsDeleted() {
		RedisSession toSave = this.repository.createSession().block();
		String sessionId = toSave.getId();
		this.repository.save(toSave).block();

		this.repository.deleteById(sessionId).block();

		toSave.changeSessionId();
		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNull();
		assertThat(this.repository.findById(sessionId).block()).isNull();
	}

	@Test // gh-1270
	void changeSessionIdSaveConcurrently() {
		RedisSession toSave = this.repository.createSession().block();
		String originalId = toSave.getId();
		this.repository.save(toSave).block();

		RedisSession copy1 = this.repository.findById(originalId).block();
		RedisSession copy2 = this.repository.findById(originalId).block();

		copy1.changeSessionId();
		this.repository.save(copy1).block();
		copy2.changeSessionId();
		this.repository.save(copy2).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(copy1.getId()).block()).isNotNull();
		assertThat(this.repository.findById(copy2.getId()).block()).isNull();
	}

	// gh-1743
	@Test
	void saveChangeSessionIdWhenFailedRenameOperationExceptionContainsMoreDetailsThenIgnoreError() {
		RedisSession toSave = this.repository.createSession().block();
		String sessionId = toSave.getId();

		this.repository.save(toSave).block();
		RedisSession session = this.repository.findById(sessionId).block();
		this.repository.deleteById(sessionId).block();
		session.changeSessionId();

		assertThatNoException().isThrownBy(() -> this.repository.save(session).block());
	}

	private String getSecurityName() {
		return this.context.getAuthentication().getName();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedWebSession
	@Import(AbstractRedisITests.BaseConfig.class)
	static class Config {

		@Bean
		SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}

	}

}
