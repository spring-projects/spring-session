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

package org.springframework.session.data.redis;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.PrincipalNameResolver;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession;
import org.springframework.session.events.AbstractSessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
public class RedisOperationsSessionRepositoryTests {
	static final String SPRING_SECURITY_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";

	@Mock
	RedisConnectionFactory factory;
	@Mock
	RedisConnection connection;
	@Mock
	RedisOperations<Object, Object> redisOperations;
	@Mock
	BoundValueOperations<Object, Object> boundValueOperations;
	@Mock
	BoundHashOperations<Object, Object, Object> boundHashOperations;
	@Mock
	BoundSetOperations<Object, Object> boundSetOperations;
	@Mock
	ApplicationEventPublisher publisher;
	@Mock
	RedisSerializer<Object> defaultSerializer;
	@Captor
	ArgumentCaptor<AbstractSessionEvent> event;
	@Captor
	ArgumentCaptor<Map<String, Object>> delta;

	private MapSession cached;

	private RedisOperationsSessionRepository redisRepository;

	@Before
	public void setup() {
		this.redisRepository = new RedisOperationsSessionRepository(this.redisOperations);
		this.redisRepository.setDefaultSerializer(this.defaultSerializer);

		this.cached = new MapSession();
		this.cached.setId("session-id");
		this.cached.setCreationTime(1404360000000L);
		this.cached.setLastAccessedTime(1404360000000L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorNullConnectionFactory() {
		new RedisOperationsSessionRepository((RedisConnectionFactory) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setApplicationEventPublisherNull() {
		this.redisRepository.setApplicationEventPublisher(null);
	}

	// gh-61
	@Test
	public void constructorConnectionFactory() {
		this.redisRepository = new RedisOperationsSessionRepository(this.factory);
		RedisSession session = this.redisRepository.createSession();

		given(this.factory.getConnection()).willReturn(this.connection);

		this.redisRepository.save(session);
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() throws Exception {
		ExpiringSession session = this.redisRepository.createSession();
		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() throws Exception {
		int interval = 1;
		this.redisRepository.setDefaultMaxInactiveInterval(interval);
		ExpiringSession session = this.redisRepository.createSession();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(interval);
	}

	@Test
	public void saveNewSession() {
		RedisSession session = this.redisRepository.createSession();
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		Map<String, Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta
				.get(RedisOperationsSessionRepository.CREATION_TIME_ATTR);
		assertThat(creationTime).isEqualTo(session.getCreationTime());
		assertThat(delta.get(RedisOperationsSessionRepository.MAX_INACTIVE_ATTR))
				.isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		assertThat(delta.get(RedisOperationsSessionRepository.LAST_ACCESSED_ATTR))
				.isEqualTo(session.getCreationTime());
	}

	// gh-467
	@Test
	public void saveSessionNothingChanged() {
		RedisSession session = this.redisRepository.new RedisSession(this.cached);

		this.redisRepository.save(session);

		verifyZeroInteractions(this.redisOperations);
	}

	@Test
	public void saveJavadocSummary() {
		RedisSession session = this.redisRepository.createSession();

		String sessionKey = "spring:session:sessions:" + session.getId();
		String backgroundExpireKey = "spring:session:expirations:"
				+ RedisSessionExpirationPolicy.roundUpToNextMinute(
						RedisSessionExpirationPolicy.expiresInMillis(session));
		String destroyedTriggerKey = "spring:session:sessions:expires:" + session.getId();

		given(this.redisOperations.boundHashOps(sessionKey))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(backgroundExpireKey))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(destroyedTriggerKey))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data
		// can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since
		// getSession checks if it is expired
		long fiveMinutesAfterExpires = session.getMaxInactiveIntervalInSeconds()
				+ TimeUnit.MINUTES.toSeconds(5);
		verify(this.boundHashOperations).expire(fiveMinutesAfterExpires,
				TimeUnit.SECONDS);
		verify(this.boundSetOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(this.boundSetOperations).add("expires:" + session.getId());
		verify(this.boundValueOperations).expire(1800L, TimeUnit.SECONDS);
		verify(this.boundValueOperations).append("");
	}

	@Test
	public void saveJavadoc() {
		RedisSession session = this.redisRepository.new RedisSession(this.cached);
		session.setLastAccessedTime(session.getLastAccessedTime());

		given(this.redisOperations.boundHashOps("spring:session:sessions:session-id"))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations
				.boundSetOps("spring:session:expirations:1404361860000"))
						.willReturn(this.boundSetOperations);
		given(this.redisOperations
				.boundValueOps("spring:session:sessions:expires:session-id"))
						.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data
		// can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since
		// getSession checks if it is expired
		verify(this.boundHashOperations).expire(
				session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5),
				TimeUnit.SECONDS);
	}

	@Test
	public void saveLastAccessChanged() {
		RedisSession session = this.redisRepository.new RedisSession(
				new MapSession(this.cached));
		session.setLastAccessedTime(12345678L);
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		assertThat(getDelta())
				.isEqualTo(map(RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
						session.getLastAccessedTime()));
	}

	@Test
	public void saveSetAttribute() {
		String attrName = "attrName";
		RedisSession session = this.redisRepository.new RedisSession(new MapSession());
		session.setAttribute(attrName, "attrValue");
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(
				map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
						session.getAttribute(attrName)));
	}

	@Test
	public void saveRemoveAttribute() {
		String attrName = "attrName";
		RedisSession session = this.redisRepository.new RedisSession(new MapSession());
		session.removeAttribute(attrName);
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(
				RedisOperationsSessionRepository.getSessionAttrNameKey(attrName), null));
	}

	@Test
	public void saveExpired() {
		RedisSession session = this.redisRepository.new RedisSession(new MapSession());
		session.setMaxInactiveIntervalInSeconds(0);
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.save(session);

		String id = session.getId();
		verify(this.redisOperations, atLeastOnce()).delete(getKey("expires:" + id));
		verify(this.redisOperations, never()).boundValueOps(getKey("expires:" + id));
	}

	@Test
	public void redisSessionGetAttributes() {
		String attrName = "attrName";
		RedisSession session = this.redisRepository.new RedisSession();
		assertThat(session.getAttributeNames()).isEmpty();
		session.setAttribute(attrName, "attrValue");
		assertThat(session.getAttributeNames()).containsOnly(attrName);
		session.removeAttribute(attrName);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void delete() {
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
		expected.setAttribute(attrName, "attrValue");
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);
		Map map = map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
				expected.getAttribute(attrName),
				RedisOperationsSessionRepository.CREATION_TIME_ATTR,
				expected.getCreationTime(),
				RedisOperationsSessionRepository.MAX_INACTIVE_ATTR,
				expected.getMaxInactiveIntervalInSeconds(),
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
				expected.getLastAccessedTime());
		given(this.boundHashOperations.entries()).willReturn(map);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);

		String id = expected.getId();
		this.redisRepository.delete(id);

		assertThat(getDelta().get(RedisOperationsSessionRepository.MAX_INACTIVE_ATTR))
				.isEqualTo(0);
		verify(this.redisOperations, atLeastOnce()).delete(getKey("expires:" + id));
		verify(this.redisOperations, never()).boundValueOps(getKey("expires:" + id));
	}

	@Test
	public void deleteNullSession() {
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		String id = "abc";
		this.redisRepository.delete(id);
		verify(this.redisOperations, times(0)).delete(anyString());
		verify(this.redisOperations, times(0)).delete(anyString());
	}

	@Test
	public void getSessionNotFound() {
		String id = "abc";
		given(this.redisOperations.boundHashOps(getKey(id)))
				.willReturn(this.boundHashOperations);
		given(this.boundHashOperations.entries()).willReturn(map());

		assertThat(this.redisRepository.getSession(id)).isNull();
	}

	@Test
	public void getSessionFound() {
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
		expected.setAttribute(attrName, "attrValue");
		given(this.redisOperations.boundHashOps(getKey(expected.getId())))
				.willReturn(this.boundHashOperations);
		Map map = map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
				expected.getAttribute(attrName),
				RedisOperationsSessionRepository.CREATION_TIME_ATTR,
				expected.getCreationTime(),
				RedisOperationsSessionRepository.MAX_INACTIVE_ATTR,
				expected.getMaxInactiveIntervalInSeconds(),
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
				expected.getLastAccessedTime());
		given(this.boundHashOperations.entries()).willReturn(map);

		RedisSession session = this.redisRepository.getSession(expected.getId());
		assertThat(session.getId()).isEqualTo(expected.getId());
		assertThat(session.getAttributeNames()).isEqualTo(expected.getAttributeNames());
		assertThat(session.getAttribute(attrName))
				.isEqualTo(expected.getAttribute(attrName));
		assertThat(session.getCreationTime()).isEqualTo(expected.getCreationTime());
		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(expected.getMaxInactiveIntervalInSeconds());
		assertThat(session.getLastAccessedTime())
				.isEqualTo(expected.getLastAccessedTime());

	}

	@Test
	public void getSessionExpired() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundHashOps(getKey(expiredId)))
				.willReturn(this.boundHashOperations);
		Map map = map(RedisOperationsSessionRepository.MAX_INACTIVE_ATTR, 1,
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
				System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		given(this.boundHashOperations.entries()).willReturn(map);

		assertThat(this.redisRepository.getSession(expiredId)).isNull();
	}

	@Test
	public void findByPrincipalNameExpired() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.boundSetOperations.members())
				.willReturn(Collections.<Object>singleton(expiredId));
		given(this.redisOperations.boundHashOps(getKey(expiredId)))
				.willReturn(this.boundHashOperations);
		Map map = map(RedisOperationsSessionRepository.MAX_INACTIVE_ATTR, 1,
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
				System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		given(this.boundHashOperations.entries()).willReturn(map);

		assertThat(this.redisRepository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "principal"))
						.isEmpty();
	}

	@Test
	public void findByPrincipalName() {
		long lastAccessed = System.currentTimeMillis() - 10;
		long createdTime = lastAccessed - 10;
		int maxInactive = 3600;
		String sessionId = "some-id";
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.boundSetOperations.members())
				.willReturn(Collections.<Object>singleton(sessionId));
		given(this.redisOperations.boundHashOps(getKey(sessionId)))
				.willReturn(this.boundHashOperations);
		Map map = map(RedisOperationsSessionRepository.CREATION_TIME_ATTR, createdTime,
				RedisOperationsSessionRepository.MAX_INACTIVE_ATTR, maxInactive,
				RedisOperationsSessionRepository.LAST_ACCESSED_ATTR, lastAccessed);
		given(this.boundHashOperations.entries()).willReturn(map);

		Map<String, RedisSession> sessionIdToSessions = this.redisRepository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						"principal");

		assertThat(sessionIdToSessions).hasSize(1);
		RedisSession session = sessionIdToSessions.get(sessionId);
		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo(sessionId);
		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessed);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(maxInactive);
		assertThat(session.getCreationTime()).isEqualTo(createdTime);
	}

	@Test
	public void cleanupExpiredSessions() {
		String expiredId = "expired-id";
		given(this.redisOperations.boundHashOps(getKey(expiredId)))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);

		Set<Object> expiredIds = new HashSet<Object>(
				Arrays.asList("expired-key1", "expired-key2"));
		given(this.boundSetOperations.members()).willReturn(expiredIds);

		this.redisRepository.cleanupExpiredSessions();

		for (Object id : expiredIds) {
			String expiredKey = "spring:session:sessions:" + id;
			// https://github.com/spring-projects/spring-session/issues/93
			verify(this.redisOperations).hasKey(expiredKey);
		}
	}

	@Test
	public void onMessageCreated() throws Exception {
		MapSession session = this.cached;
		byte[] pattern = "".getBytes("UTF-8");
		String channel = "spring:session:event:created:" + session.getId();
		JdkSerializationRedisSerializer defaultSerailizer = new JdkSerializationRedisSerializer();
		this.redisRepository.setDefaultSerializer(defaultSerailizer);
		byte[] body = defaultSerailizer.serialize(new HashMap());
		DefaultMessage message = new DefaultMessage(channel.getBytes("UTF-8"), body);

		this.redisRepository.setApplicationEventPublisher(this.publisher);

		this.redisRepository.onMessage(message, pattern);

		verify(this.publisher).publishEvent(this.event.capture());
		assertThat(this.event.getValue().getSessionId()).isEqualTo(session.getId());
	}

	// gh-309
	@Test
	public void onMessageCreatedCustomSerializer() throws Exception {
		MapSession session = this.cached;
		byte[] pattern = "".getBytes("UTF-8");
		byte[] body = new byte[0];
		String channel = "spring:session:event:created:" + session.getId();
		given(this.defaultSerializer.deserialize(body))
				.willReturn(new HashMap<String, Object>());
		DefaultMessage message = new DefaultMessage(channel.getBytes("UTF-8"), body);
		this.redisRepository.setApplicationEventPublisher(this.publisher);

		this.redisRepository.onMessage(message, pattern);

		verify(this.publisher).publishEvent(this.event.capture());
		assertThat(this.event.getValue().getSessionId()).isEqualTo(session.getId());
		verify(this.defaultSerializer).deserialize(body);
	}

	@Test
	public void resolvePrincipalIndex() {
		PrincipalNameResolver resolver = RedisOperationsSessionRepository.PRINCIPAL_NAME_RESOLVER;
		String username = "username";
		RedisSession session = this.redisRepository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				username);

		assertThat(resolver.resolvePrincipal(session)).isEqualTo(username);
	}

	@Test
	public void resolveIndexOnSecurityContext() {
		String principal = "resolveIndexOnSecurityContext";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal,
				"notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(authentication);

		PrincipalNameResolver resolver = RedisOperationsSessionRepository.PRINCIPAL_NAME_RESOLVER;

		RedisSession session = this.redisRepository.createSession();
		session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);

		assertThat(resolver.resolvePrincipal(session)).isEqualTo(principal);
	}

	@Test
	public void flushModeOnSaveCreate() {
		this.redisRepository.createSession();

		verifyZeroInteractions(this.boundHashOperations);
	}

	@Test
	public void flushModeOnSaveSetAttribute() {
		RedisSession session = this.redisRepository.createSession();
		session.setAttribute("something", "here");

		verifyZeroInteractions(this.boundHashOperations);
	}

	@Test
	public void flushModeOnSaveRemoveAttribute() {
		RedisSession session = this.redisRepository.createSession();
		session.removeAttribute("remove");

		verifyZeroInteractions(this.boundHashOperations);
	}

	@Test
	public void flushModeOnSaveSetLastAccessedTime() {
		RedisSession session = this.redisRepository.createSession();
		session.setLastAccessedTime(1L);

		verifyZeroInteractions(this.boundHashOperations);
	}

	@Test
	public void flushModeOnSaveSetMaxInactiveIntervalInSeconds() {
		RedisSession session = this.redisRepository.createSession();
		session.setMaxInactiveIntervalInSeconds(1);

		verifyZeroInteractions(this.boundHashOperations);
	}

	@Test
	public void flushModeImmediateCreate() {
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();

		Map<String, Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta
				.get(RedisOperationsSessionRepository.CREATION_TIME_ATTR);
		assertThat(creationTime).isEqualTo(session.getCreationTime());
		assertThat(delta.get(RedisOperationsSessionRepository.MAX_INACTIVE_ATTR))
				.isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		assertThat(delta.get(RedisOperationsSessionRepository.LAST_ACCESSED_ATTR))
				.isEqualTo(session.getCreationTime());
	}

	@Test
	public void flushModeImmediateSetAttribute() {
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();
		String attrName = "someAttribute";
		session.setAttribute(attrName, "someValue");

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(
				map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
						session.getAttribute(attrName)));
	}

	@Test
	public void flushModeImmediateRemoveAttribute() {
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();
		String attrName = "someAttribute";
		session.removeAttribute(attrName);

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(
				map(RedisOperationsSessionRepository.getSessionAttrNameKey(attrName),
						session.getAttribute(attrName)));
	}

	@Test
	public void flushModeSetMaxInactiveIntervalInSeconds() {
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();

		reset(this.boundHashOperations);

		session.setMaxInactiveIntervalInSeconds(1);

		verify(this.boundHashOperations).expire(anyLong(), any(TimeUnit.class));
	}

	@Test
	public void flushModeSetLastAccessedTime() {
		given(this.redisOperations.boundHashOps(anyString()))
				.willReturn(this.boundHashOperations);
		given(this.redisOperations.boundSetOps(anyString()))
				.willReturn(this.boundSetOperations);
		given(this.redisOperations.boundValueOps(anyString()))
				.willReturn(this.boundValueOperations);

		this.redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = this.redisRepository.createSession();

		long now = System.currentTimeMillis();
		session.setLastAccessedTime(now);

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta)
				.isEqualTo(map(RedisOperationsSessionRepository.LAST_ACCESSED_ATTR,
						session.getLastAccessedTime()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void setRedisFlushModeNull() {
		this.redisRepository.setRedisFlushMode(null);
	}

	private String getKey(String id) {
		return "spring:session:sessions:" + id;
	}

	private Map map(Object... objects) {
		Map<String, Object> result = new HashMap<String, Object>();
		if (objects == null) {
			return result;
		}
		for (int i = 0; i < objects.length; i += 2) {
			result.put((String) objects[i], objects[i + 1]);
		}
		return result;
	}

	private Map<String, Object> getDelta() {
		return getDelta(1);
	}

	private Map<String, Object> getDelta(int times) {
		verify(this.boundHashOperations, times(times)).putAll(this.delta.capture());
		return this.delta.getAllValues().get(times - 1);
	}
}
