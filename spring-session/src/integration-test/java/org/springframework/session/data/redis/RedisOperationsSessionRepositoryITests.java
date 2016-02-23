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
package org.springframework.session.data.redis;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.SessionEventRegistry;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class RedisOperationsSessionRepositoryITests {
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private RedisOperationsSessionRepository repository;

	@Autowired
	private SessionEventRegistry registry;

	@Autowired
	RedisOperations<Object, Object> redis;

	SecurityContext context;

	SecurityContext changedContext;

	@Before
	public void setup() {
		registry.clear();
		context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken("username-"+UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		changedContext = SecurityContextHolder.createEmptyContext();
		changedContext.setAuthentication(new UsernamePasswordAuthenticationToken("changedContext-"+UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));
	}

	@Test
	public void saves() throws InterruptedException {
		String username = "saves-" + System.currentTimeMillis();

		String usernameSessionKey = "spring:session:RedisOperationsSessionRepositoryITests:index:" + INDEX_NAME + ":"
				+ username;

		RedisSession toSave = repository.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, toSaveContext);
		toSave.setAttribute(INDEX_NAME, username);
		registry.clear();

		repository.save(toSave);

		assertThat(registry.receivedEvent(toSave.getId())).isTrue();
		assertThat(registry.getEvent(toSave.getId())).isInstanceOf(SessionCreatedEvent.class);
		assertThat(redis.boundSetOps(usernameSessionKey).members()).contains(toSave.getId());

		Session session = repository.getSession(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.getAttribute(expectedAttributeName)).isEqualTo(toSave.getAttribute(expectedAttributeName));

		registry.clear();

		repository.delete(toSave.getId());

		assertThat(repository.getSession(toSave.getId())).isNull();
		assertThat(registry.getEvent(toSave.getId())).isInstanceOf(SessionDestroyedEvent.class);
		assertThat(redis.boundSetOps(usernameSessionKey).members()).doesNotContain(toSave.getId());

		assertThat(registry.getEvent(toSave.getId()).getSession().getAttribute(expectedAttributeName))
				.isEqualTo(expectedAttributeValue);
	}

	@Test
	public void putAllOnSingleAttrDoesNotRemoveOld() {
		RedisSession toSave = repository.createSession();
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
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		repository.delete(toSave.getId());
		assertThat(registry.receivedEvent(toSave.getId())).isTrue();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameExpireRemovesIndex() throws Exception {
		String principalName = "findByPrincipalNameExpireRemovesIndex" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		String body = "spring:session:RedisOperationsSessionRepositoryITests:sessions:expires:" + toSave.getId();
		String channel = ":expired";
		DefaultMessage message = new DefaultMessage(channel.getBytes("UTF-8"), body.getBytes("UTF-8"));
		byte[] pattern = new byte[] {};
		repository.onMessage(message, pattern);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChange() throws Exception {
		String principalName = "findByPrincipalNameNoPrincipalNameChange" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoPrincipalNameChangeReload() throws Exception {
		String principalName = "findByPrincipalNameNoPrincipalNameChangeReload" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedPrincipalName() throws Exception {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, null);
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalName() throws Exception {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		toSave.setAttribute(INDEX_NAME, principalNameChanged);
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedPrincipalNameReload() throws Exception {
		String principalName = "findByDeletedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		RedisSession getSession = repository.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedPrincipalNameReload() throws Exception {
		String principalName = "findByChangedPrincipalName" + UUID.randomUUID();
		String principalNameChanged = "findByChangedPrincipalName" + UUID.randomUUID();
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(INDEX_NAME, principalName);

		repository.save(toSave);

		RedisSession getSession = repository.getSession(toSave.getId());

		getSession.setAttribute(INDEX_NAME, principalNameChanged);
		repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				principalName);
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, principalNameChanged);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findBySecurityPrincipalName() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());

		repository.delete(toSave.getId());
		assertThat(registry.receivedEvent(toSave.getId())).isTrue();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findBySecurityPrincipalNameExpireRemovesIndex() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		String body = "spring:session:RedisOperationsSessionRepositoryITests:sessions:expires:" + toSave.getId();
		String channel = ":expired";
		DefaultMessage message = new DefaultMessage(channel.getBytes("UTF-8"), body.getBytes("UTF-8"));
		byte[] pattern = new byte[] {};
		repository.onMessage(message, pattern);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(0);
		assertThat(findByPrincipalName.keySet()).doesNotContain(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChange() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChangeReload() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedSecurityPrincipalName() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, null);
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalName() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, changedContext);
		repository.save(toSave);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findByDeletedSecurityPrincipalNameReload() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		RedisSession getSession = repository.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getChangedSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByChangedSecurityPrincipalNameReload() throws Exception {
		RedisSession toSave = repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		repository.save(toSave);

		RedisSession getSession = repository.getSession(toSave.getId());

		getSession.setAttribute(SPRING_SECURITY_CONTEXT, changedContext);
		repository.save(getSession);

		Map<String, RedisSession> findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME,
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = repository.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	private String getSecurityName() {
		return context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return changedContext.getAuthentication().getName();
	}

	@Configuration
	@EnableRedisHttpSession(redisNamespace = "RedisOperationsSessionRepositoryITests")
	static class Config {
		@Bean
		public JedisConnectionFactory connectionFactory() throws Exception {
			JedisConnectionFactory factory = new JedisConnectionFactory();
			factory.setUsePool(false);
			return factory;
		}

		@Bean
		public SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}
	}
}