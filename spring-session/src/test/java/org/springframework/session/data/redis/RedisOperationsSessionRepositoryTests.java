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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.CREATION_TIME_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.LAST_ACCESSED_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.MAX_INACTIVE_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.getSessionAttrNameKey;

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


@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"unchecked","rawtypes"})
public class RedisOperationsSessionRepositoryTests {
	static final String SPRING_SECURITY_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";

	@Mock
	RedisConnectionFactory factory;
	@Mock
	RedisConnection connection;
	@Mock
	RedisOperations<Object,Object> redisOperations;
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
	ArgumentCaptor<Map<String,Object>> delta;

	private MapSession cached;

	private RedisOperationsSessionRepository redisRepository;


	@Before
	public void setup() {
		this.redisRepository = new RedisOperationsSessionRepository(redisOperations);
		this.redisRepository.setDefaultSerializer(defaultSerializer);

		cached = new MapSession();
		cached.setId("session-id");
		cached.setCreationTime(1404360000000L);
		cached.setLastAccessedTime(1404360000000L);
	}

	@Test(expected=IllegalArgumentException.class)
	public void constructorNullConnectionFactory() {
		new RedisOperationsSessionRepository((RedisConnectionFactory)null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void setApplicationEventPublisherNull() {
		redisRepository.setApplicationEventPublisher(null);
	}

	// gh-61
	@Test
	public void constructorConnectionFactory() {
		redisRepository = new RedisOperationsSessionRepository(factory);
		RedisSession session = redisRepository.createSession();

		when(factory.getConnection()).thenReturn(connection);

		redisRepository.save(session);
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() throws Exception {
		ExpiringSession session = redisRepository.createSession();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() throws Exception {
		int interval = 1;
		redisRepository.setDefaultMaxInactiveInterval(interval);
		ExpiringSession session = redisRepository.createSession();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(interval);
	}

	@Test
	public void saveNewSession() {
		RedisSession session = redisRepository.createSession();
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		Map<String,Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta.get(CREATION_TIME_ATTR);
		assertThat(creationTime).isEqualTo(session.getCreationTime());
		assertThat(delta.get(MAX_INACTIVE_ATTR)).isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		assertThat(delta.get(LAST_ACCESSED_ATTR)).isEqualTo(session.getCreationTime());
	}

	@Test
	public void saveJavadocSummary() {
		RedisSession session = redisRepository.createSession();

		String sessionKey = "spring:session:sessions:" + session.getId();
		String backgroundExpireKey = "spring:session:expirations:" + RedisSessionExpirationPolicy.roundUpToNextMinute(RedisSessionExpirationPolicy.expiresInMillis(session));
		String destroyedTriggerKey = "spring:session:sessions:expires:" + session.getId();

		when(redisOperations.boundHashOps(sessionKey)).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(backgroundExpireKey)).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(destroyedTriggerKey)).thenReturn(boundValueOperations);

		redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since getSession checks if it is expired
		long fiveMinutesAfterExpires = session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5);
		verify(boundHashOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(boundSetOperations).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
		verify(boundSetOperations).add("expires:" + session.getId());
		verify(boundValueOperations).expire(1800L, TimeUnit.SECONDS);
		verify(boundValueOperations).append("");
	}

	@Test
	public void saveJavadoc() {
		RedisSession session = redisRepository.new RedisSession(cached);

		when(redisOperations.boundHashOps("spring:session:sessions:session-id")).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps("spring:session:expirations:1404361860000")).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps("spring:session:sessions:expires:session-id")).thenReturn(boundValueOperations);

		redisRepository.save(session);

		// the actual data in the session expires 5 minutes after expiration so the data can be accessed in expiration events
		// if the session is retrieved and expired it will not be returned since getSession checks if it is expired
		verify(boundHashOperations).expire(session.getMaxInactiveIntervalInSeconds() + TimeUnit.MINUTES.toSeconds(5), TimeUnit.SECONDS);
	}

	@Test
	public void saveLastAccessChanged() {
		RedisSession session = redisRepository.new RedisSession(new MapSession(cached));
		session.setLastAccessedTime(12345678L);
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(LAST_ACCESSED_ATTR, session.getLastAccessedTime()));
	}

	@Test
	public void saveSetAttribute() {
		String attrName = "attrName";
		RedisSession session = redisRepository.new RedisSession(new MapSession());
		session.setAttribute(attrName, "attrValue");
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	public void saveRemoveAttribute() {
		String attrName = "attrName";
		RedisSession session = redisRepository.new RedisSession(new MapSession());
		session.removeAttribute(attrName);
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		assertThat(getDelta()).isEqualTo(map(getSessionAttrNameKey(attrName), null));
	}

	@Test
	public void saveExpired() {
		RedisSession session = redisRepository.new RedisSession(new MapSession());
		session.setMaxInactiveIntervalInSeconds(0);
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.save(session);

		String id = session.getId();
		verify(redisOperations,atLeastOnce()).delete(getKey("expires:"+id));
		verify(redisOperations,never()).boundValueOps(getKey("expires:"+id));
	}

	@Test
	public void redisSessionGetAttributes() {
		String attrName = "attrName";
		RedisSession session = redisRepository.new RedisSession();
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
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);
		Map map = map(
				getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
				CREATION_TIME_ATTR, expected.getCreationTime(),
				MAX_INACTIVE_ATTR, expected.getMaxInactiveIntervalInSeconds(),
				LAST_ACCESSED_ATTR, expected.getLastAccessedTime());
		when(boundHashOperations.entries()).thenReturn(map);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

		String id = expected.getId();
		redisRepository.delete(id);

		assertThat(getDelta().get(MAX_INACTIVE_ATTR)).isEqualTo(0);
		verify(redisOperations,atLeastOnce()).delete(getKey("expires:"+id));
		verify(redisOperations,never()).boundValueOps(getKey("expires:"+id));
	}

	@Test
	public void deleteNullSession() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		String id = "abc";
		redisRepository.delete(id);
		verify(redisOperations,times(0)).delete(anyString());
		verify(redisOperations,times(0)).delete(anyString());
	}

	@Test
	public void getSessionNotFound() {
		String id = "abc";
		when(redisOperations.boundHashOps(getKey(id))).thenReturn(boundHashOperations);
		when(boundHashOperations.entries()).thenReturn(map());

		assertThat(redisRepository.getSession(id)).isNull();
	}

	@Test
	public void getSessionFound() {
		String attrName = "attrName";
		MapSession expected = new MapSession();
		expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
		expected.setAttribute(attrName, "attrValue");
		when(redisOperations.boundHashOps(getKey(expected.getId()))).thenReturn(boundHashOperations);
		Map map = map(
				getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
				CREATION_TIME_ATTR, expected.getCreationTime(),
				MAX_INACTIVE_ATTR, expected.getMaxInactiveIntervalInSeconds(),
				LAST_ACCESSED_ATTR, expected.getLastAccessedTime());
		when(boundHashOperations.entries()).thenReturn(map);

		RedisSession session = redisRepository.getSession(expected.getId());
		assertThat(session.getId()).isEqualTo(expected.getId());
		assertThat(session.getAttributeNames()).isEqualTo(expected.getAttributeNames());
		assertThat(session.getAttribute(attrName)).isEqualTo(expected.getAttribute(attrName));
		assertThat(session.getCreationTime()).isEqualTo(expected.getCreationTime());
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expected.getMaxInactiveIntervalInSeconds());
		assertThat(session.getLastAccessedTime()).isEqualTo(expected.getLastAccessedTime());

	}

	@Test
	public void getSessionExpired() {
		String expiredId = "expired-id";
		when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
		Map map = map(
				MAX_INACTIVE_ATTR, 1,
				LAST_ACCESSED_ATTR, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		when(boundHashOperations.entries()).thenReturn(map);

		assertThat(redisRepository.getSession(expiredId)).isNull();
	}

	@Test
	public void findByPrincipalNameExpired() {
		String expiredId = "expired-id";
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(boundSetOperations.members()).thenReturn(Collections.<Object>singleton(expiredId));
		when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
		Map map = map(
				MAX_INACTIVE_ATTR, 1,
				LAST_ACCESSED_ATTR, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		when(boundHashOperations.entries()).thenReturn(map);

		assertThat(redisRepository.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "principal")).isEmpty();
	}

	@Test
	public void findByPrincipalName() {
		long lastAccessed = System.currentTimeMillis() - 10;
		long createdTime = lastAccessed - 10;
		int maxInactive = 3600;
		String sessionId = "some-id";
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(boundSetOperations.members()).thenReturn(Collections.<Object>singleton(sessionId));
		when(redisOperations.boundHashOps(getKey(sessionId))).thenReturn(boundHashOperations);
		Map map = map(
				CREATION_TIME_ATTR, createdTime,
				MAX_INACTIVE_ATTR, maxInactive,
				LAST_ACCESSED_ATTR, lastAccessed);
		when(boundHashOperations.entries()).thenReturn(map);

		Map<String, RedisSession> sessionIdToSessions = redisRepository.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "principal");

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
		when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

		Set<Object> expiredIds = new HashSet<Object>(Arrays.asList("expired-key1","expired-key2"));
		when(boundSetOperations.members()).thenReturn(expiredIds);

		redisRepository.cleanupExpiredSessions();

		for(Object id : expiredIds) {
			String expiredKey = "spring:session:sessions:" + id;
			// https://github.com/spring-projects/spring-session/issues/93
			verify(redisOperations).hasKey(expiredKey);
		}
	}

	@Test
	public void onMessageCreated() throws Exception {
		MapSession session = cached;
		byte[] pattern = "".getBytes("UTF-8");
		String channel = "spring:session:event:created:" + session.getId();
		JdkSerializationRedisSerializer defaultSerailizer = new JdkSerializationRedisSerializer();
		redisRepository.setDefaultSerializer(defaultSerailizer);
		byte[] body = defaultSerailizer.serialize(new HashMap());
		DefaultMessage message = new DefaultMessage(channel.getBytes("UTF-8"), body);

		redisRepository.setApplicationEventPublisher(publisher);

		redisRepository.onMessage(message, pattern);

		verify(publisher).publishEvent(event.capture());
		assertThat(event.getValue().getSessionId()).isEqualTo(session.getId());
	}

	// gh-309
	@Test
	public void onMessageCreatedCustomSerializer() throws Exception {
		MapSession session = cached;
		byte[] pattern = "".getBytes("UTF-8");
		byte[] body = new byte[0];
		String channel = "spring:session:event:created:" + session.getId();
		when(defaultSerializer.deserialize(body)).thenReturn(new HashMap<String,Object>());
		DefaultMessage message = new DefaultMessage(channel.getBytes("UTF-8"), body);
		redisRepository.setApplicationEventPublisher(publisher);

		redisRepository.onMessage(message, pattern);

		verify(publisher).publishEvent(event.capture());
		assertThat(event.getValue().getSessionId()).isEqualTo(session.getId());
		verify(defaultSerializer).deserialize(body);
	}

	@Test
	public void resolvePrincipalIndex() {
		PrincipalNameResolver resolver = RedisOperationsSessionRepository.PRINCIPAL_NAME_RESOLVER;
		String username = "username";
		RedisSession session = redisRepository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

		assertThat(resolver.resolvePrincipal(session)).isEqualTo(username);
	}

	@Test
	public void resolveIndexOnSecurityContext() {
		String principal = "resolveIndexOnSecurityContext";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(authentication);

		PrincipalNameResolver resolver = RedisOperationsSessionRepository.PRINCIPAL_NAME_RESOLVER;

		RedisSession session = redisRepository.createSession();
		session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);

		assertThat(resolver.resolvePrincipal(session)).isEqualTo(principal);
	}

	@Test
	public void flushModeOnSaveCreate() {
		redisRepository.createSession();

		verifyZeroInteractions(boundHashOperations);
	}

	@Test
	public void flushModeOnSaveSetAttribute() {
		RedisSession session = redisRepository.createSession();
		session.setAttribute("something", "here");

		verifyZeroInteractions(boundHashOperations);
	}

	@Test
	public void flushModeOnSaveRemoveAttribute() {
		RedisSession session = redisRepository.createSession();
		session.removeAttribute("remove");

		verifyZeroInteractions(boundHashOperations);
	}

	@Test
	public void flushModeOnSaveSetLastAccessedTime() {
		RedisSession session = redisRepository.createSession();
		session.setLastAccessedTime(1L);

		verifyZeroInteractions(boundHashOperations);
	}

	@Test
	public void flushModeOnSaveSetMaxInactiveIntervalInSeconds() {
		RedisSession session = redisRepository.createSession();
		session.setMaxInactiveIntervalInSeconds(1);

		verifyZeroInteractions(boundHashOperations);
	}

	@Test
	public void flushModeImmediateCreate() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = redisRepository.createSession();

		Map<String, Object> delta = getDelta();
		assertThat(delta.size()).isEqualTo(3);
		Object creationTime = delta.get(CREATION_TIME_ATTR);
		assertThat(creationTime).isEqualTo(session.getCreationTime());
		assertThat(delta.get(MAX_INACTIVE_ATTR)).isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		assertThat(delta.get(LAST_ACCESSED_ATTR)).isEqualTo(session.getCreationTime());
	}

	@Test
	public void flushModeImmediateSetAttribute() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = redisRepository.createSession();
		String attrName = "someAttribute";
		session.setAttribute(attrName, "someValue");

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(map(getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	public void flushModeImmediateRemoveAttribute() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = redisRepository.createSession();
		String attrName = "someAttribute";
		session.removeAttribute(attrName);

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(map(getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
	}

	@Test
	public void flushModeSetMaxInactiveIntervalInSeconds() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = redisRepository.createSession();

		reset(boundHashOperations);

		session.setMaxInactiveIntervalInSeconds(1);

		verify(boundHashOperations).expire(anyLong(), any(TimeUnit.class));
	}

	@Test
	public void flushModeSetLastAccessedTime() {
		when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);
		when(redisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
		when(redisOperations.boundValueOps(anyString())).thenReturn(boundValueOperations);

		redisRepository.setRedisFlushMode(RedisFlushMode.IMMEDIATE);
		RedisSession session = redisRepository.createSession();

		long now = System.currentTimeMillis();
		session.setLastAccessedTime(now);

		Map<String, Object> delta = getDelta(2);
		assertThat(delta.size()).isEqualTo(1);
		assertThat(delta).isEqualTo(map(LAST_ACCESSED_ATTR, session.getLastAccessedTime()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void setRedisFlushModeNull() {
		redisRepository.setRedisFlushMode(null);
	}

	private String getKey(String id) {
		return "spring:session:sessions:" + id;
	}

	private Map map(Object...objects) {
		Map<String,Object> result = new HashMap<String,Object>();
		if(objects == null) {
			return result;
		}
		for(int i = 0; i < objects.length; i += 2) {
			result.put((String)objects[i], objects[i+1]);
		}
		return result;
	}

	private Map<String,Object> getDelta() {
		return getDelta(1);
	}

	private Map<String,Object> getDelta(int times) {
		verify(boundHashOperations,times(times)).putAll(delta.capture());
		return delta.getAllValues().get(times - 1);
	}
}