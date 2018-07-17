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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.query.impl.predicates.EqualPredicate;
import org.junit.Before;
import org.junit.Test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.hazelcast.HazelcastSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link HazelcastSessionRepository}.
 *
 * @author Vedran Pavic
 * @author Aleksandar Stojsavljevic
 */
public class HazelcastSessionRepositoryTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);

	@SuppressWarnings("unchecked")
	private IMap<String, MapSession> sessions = mock(IMap.class);

	private HazelcastSessionRepository repository;

	@Before
	public void setUp() {
		given(this.hazelcastInstance.<String, MapSession>getMap(anyString()))
				.willReturn(this.sessions);
		this.repository = new HazelcastSessionRepository(this.hazelcastInstance);
		this.repository.init();
	}

	@Test
	public void constructorNullHazelcastInstance() {
		assertThatThrownBy(() -> new HazelcastSessionRepository(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("HazelcastInstance must not be null");
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(new MapSession().getMaxInactiveInterval());
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		int interval = 1;
		this.repository.setDefaultMaxInactiveInterval(interval);

		HazelcastSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(Duration.ofSeconds(interval));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveNewFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveNewFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedAttributeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.setAttribute("testName", "testValue");
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedAttributeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.setAttribute("testName", "testValue");
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void removeAttributeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.removeAttribute("testName");
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void removeAttributeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.removeAttribute("testName");
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedLastAccessedTimeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.setLastAccessedTime(Instant.now());
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedLastAccessedTimeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.setLastAccessedTime(Instant.now());
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedMaxInactiveIntervalInSecondsFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedMaxInactiveIntervalInSecondsFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUnchangedFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession();
		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUnchangedFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		verify(this.sessions, times(1)).set(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionNotFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String sessionId = "testSessionId";

		HazelcastSession session = this.repository.findById(sessionId);

		assertThat(session).isNull();
		verify(this.sessions, times(1)).get(eq(sessionId));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionExpired() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		MapSession expired = new MapSession();
		expired.setLastAccessedTime(Instant.now()
				.minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));
		given(this.sessions.get(eq(expired.getId()))).willReturn(expired);

		HazelcastSession session = this.repository.findById(expired.getId());

		assertThat(session).isNull();
		verify(this.sessions, times(1)).get(eq(expired.getId()));
		verify(this.sessions, times(1)).remove(eq(expired.getId()));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		MapSession saved = new MapSession();
		saved.setAttribute("savedName", "savedValue");
		given(this.sessions.get(eq(saved.getId()))).willReturn(saved);

		HazelcastSession session = this.repository.findById(saved.getId());

		assertThat(session.getId()).isEqualTo(saved.getId());
		assertThat(session.<String>getAttribute("savedName")).isEqualTo("savedValue");
		verify(this.sessions, times(1)).get(eq(saved.getId()));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void delete() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String sessionId = "testSessionId";

		this.repository.deleteById(sessionId);

		verify(this.sessions, times(1)).remove(eq(sessionId));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void findByIndexNameAndIndexValueUnknownIndexName() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String indexValue = "testIndexValue";

		Map<String, HazelcastSession> sessions = this.repository
				.findByIndexNameAndIndexValue("testIndexName", indexValue);

		assertThat(sessions).isEmpty();
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void findByIndexNameAndIndexValuePrincipalIndexNameNotFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String principal = "username";

		Map<String, HazelcastSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principal);

		assertThat(sessions).isEmpty();
		verify(this.sessions, times(1)).values(isA(EqualPredicate.class));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal,
				"notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		List<MapSession> saved = new ArrayList<>(2);
		MapSession saved1 = new MapSession();
		saved1.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved1);
		MapSession saved2 = new MapSession();
		saved2.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved2);
		given(this.sessions.values(isA(EqualPredicate.class))).willReturn(saved);

		Map<String, HazelcastSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principal);

		assertThat(sessions).hasSize(2);
		verify(this.sessions, times(1)).values(isA(EqualPredicate.class));
		verifyZeroInteractions(this.sessions);
	}

	@Test // gh-1120
	public void getAttributeNamesAndRemove() {
		HazelcastSession session = this.repository.createSession();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

}
