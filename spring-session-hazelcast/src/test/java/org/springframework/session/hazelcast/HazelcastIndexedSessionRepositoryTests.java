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

package org.springframework.session.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.query.impl.predicates.EqualPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.hazelcast.HazelcastIndexedSessionRepository.HazelcastSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link HazelcastIndexedSessionRepository}.
 *
 * @author Vedran Pavic
 * @author Aleksandar Stojsavljevic
 */
class HazelcastIndexedSessionRepositoryTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);

	@SuppressWarnings("unchecked")
	private IMap<String, MapSession> sessions = mock(IMap.class);

	private HazelcastIndexedSessionRepository repository;

	@BeforeEach
	void setUp() {
		given(this.hazelcastInstance.<String, MapSession>getMap(anyString())).willReturn(this.sessions);
		this.repository = new HazelcastIndexedSessionRepository(this.hazelcastInstance);
		this.repository.afterPropertiesSet();
	}

	@Test
	void constructorNullHazelcastInstance() {
		assertThatIllegalArgumentException().isThrownBy(() -> new HazelcastIndexedSessionRepository(null))
				.withMessage("HazelcastInstance must not be null");
	}

	@Test
	void setSaveModeNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setSaveMode(null))
				.withMessage("saveMode must not be null");
	}

	@Test
	void createSessionDefaultMaxInactiveInterval() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveInterval()).isEqualTo(new MapSession().getMaxInactiveInterval());
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void createSessionCustomMaxInactiveInterval() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		Duration interval = Duration.ofSeconds(1);
		this.repository.setDefaultMaxInactiveInterval(interval);

		HazelcastSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveInterval()).isEqualTo(interval);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveNewFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();
		verifyNoMoreInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveNewFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		this.repository.setFlushMode(FlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedAttributeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.setAttribute("testName", "testValue");
		verifyNoMoreInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedAttributeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		this.repository.setFlushMode(FlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.setAttribute("testName", "testValue");
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()), any(EntryProcessor.class));

		this.repository.save(session);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void removeAttributeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.removeAttribute("testName");
		verifyNoMoreInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void removeAttributeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		this.repository.setFlushMode(FlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.removeAttribute("testName");
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()), any(EntryProcessor.class));

		this.repository.save(session);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedLastAccessedTimeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.setLastAccessedTime(Instant.now());
		verifyNoMoreInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedLastAccessedTimeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		this.repository.setFlushMode(FlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		session.setLastAccessedTime(Instant.now());
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(session.getId()), any(EntryProcessor.class));

		this.repository.save(session);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedMaxInactiveIntervalInSecondsFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		verifyNoMoreInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedMaxInactiveIntervalInSecondsFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		this.repository.setFlushMode(FlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		verify(this.sessions, times(1)).set(eq(sessionId), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).executeOnKey(eq(sessionId), any(EntryProcessor.class));

		this.repository.save(session);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUnchangedFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		HazelcastSession session = this.repository.createSession();
		this.repository.save(session);
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));

		this.repository.save(session);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUnchangedFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		this.repository.setFlushMode(FlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession();
		verify(this.sessions, times(1)).set(eq(session.getId()), eq(session.getDelegate()), isA(Long.class),
				eq(TimeUnit.SECONDS));

		this.repository.save(session);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void getSessionNotFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		String sessionId = "testSessionId";

		HazelcastSession session = this.repository.findById(sessionId);

		assertThat(session).isNull();
		verify(this.sessions, times(1)).get(eq(sessionId));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void getSessionExpired() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		MapSession expired = new MapSession();
		expired.setLastAccessedTime(Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));
		given(this.sessions.get(eq(expired.getId()))).willReturn(expired);

		HazelcastSession session = this.repository.findById(expired.getId());

		assertThat(session).isNull();
		verify(this.sessions, times(1)).get(eq(expired.getId()));
		verify(this.sessions, times(1)).remove(eq(expired.getId()));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void getSessionFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		MapSession saved = new MapSession();
		saved.setAttribute("savedName", "savedValue");
		given(this.sessions.get(eq(saved.getId()))).willReturn(saved);

		HazelcastSession session = this.repository.findById(saved.getId());

		assertThat(session.getId()).isEqualTo(saved.getId());
		assertThat(session.<String>getAttribute("savedName")).isEqualTo("savedValue");
		verify(this.sessions, times(1)).get(eq(saved.getId()));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void delete() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		String sessionId = "testSessionId";

		this.repository.deleteById(sessionId);

		verify(this.sessions, times(1)).remove(eq(sessionId));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void findByIndexNameAndIndexValueUnknownIndexName() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		String indexValue = "testIndexValue";

		Map<String, HazelcastSession> sessions = this.repository.findByIndexNameAndIndexValue("testIndexName",
				indexValue);

		assertThat(sessions).isEmpty();
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void findByIndexNameAndIndexValuePrincipalIndexNameNotFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		String principal = "username";

		Map<String, HazelcastSession> sessions = this.repository
				.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);

		assertThat(sessions).isEmpty();
		verify(this.sessions, times(1)).values(isA(EqualPredicate.class));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class), anyBoolean());

		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "notused",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		List<MapSession> saved = new ArrayList<>(2);
		MapSession saved1 = new MapSession();
		saved1.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved1);
		MapSession saved2 = new MapSession();
		saved2.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved2);
		given(this.sessions.values(isA(EqualPredicate.class))).willReturn(saved);

		Map<String, HazelcastSession> sessions = this.repository
				.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);

		assertThat(sessions).hasSize(2);
		verify(this.sessions, times(1)).values(isA(EqualPredicate.class));
		verifyNoMoreInteractions(this.sessions);
	}

	@Test // gh-1120
	void getAttributeNamesAndRemove() {
		HazelcastSession session = this.repository.createSession();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void saveWithSaveModeOnSetAttribute() {
		verify(this.sessions).addEntryListener(any(MapListener.class), anyBoolean());
		this.repository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		HazelcastSession session = this.repository.new HazelcastSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.repository.save(session);
		ArgumentCaptor<SessionUpdateEntryProcessor> captor = ArgumentCaptor.forClass(SessionUpdateEntryProcessor.class);
		verify(this.sessions).executeOnKey(eq(session.getId()), captor.capture());
		assertThat((Map<String, Object>) ReflectionTestUtils.getField(captor.getValue(), "delta")).hasSize(1);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	@SuppressWarnings("unchecked")
	void saveWithSaveModeOnGetAttribute() {
		verify(this.sessions).addEntryListener(any(MapListener.class), anyBoolean());
		this.repository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		HazelcastSession session = this.repository.new HazelcastSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.repository.save(session);
		ArgumentCaptor<SessionUpdateEntryProcessor> captor = ArgumentCaptor.forClass(SessionUpdateEntryProcessor.class);
		verify(this.sessions).executeOnKey(eq(session.getId()), captor.capture());
		assertThat((Map<String, Object>) ReflectionTestUtils.getField(captor.getValue(), "delta")).hasSize(2);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	@SuppressWarnings("unchecked")
	void saveWithSaveModeAlways() {
		verify(this.sessions).addEntryListener(any(MapListener.class), anyBoolean());
		this.repository.setSaveMode(SaveMode.ALWAYS);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		HazelcastSession session = this.repository.new HazelcastSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");
		this.repository.save(session);
		ArgumentCaptor<SessionUpdateEntryProcessor> captor = ArgumentCaptor.forClass(SessionUpdateEntryProcessor.class);
		verify(this.sessions).executeOnKey(eq(session.getId()), captor.capture());
		assertThat((Map<String, Object>) ReflectionTestUtils.getField(captor.getValue(), "delta")).hasSize(3);
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void createSessionWhenSessionIdGenerationStrategyThenUses() {
		this.repository.setSessionIdGenerationStrategy(() -> "test");
		HazelcastSession session = this.repository.createSession();
		assertThat(session.getId()).isEqualTo("test");
		assertThat(session.changeSessionId()).isEqualTo("test");
	}

	@Test
	void setSessionIdGenerationStrategyWhenNullThenThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setSessionIdGenerationStrategy(null))
				.withMessage("sessionIdGenerationStrategy cannot be null");
	}

	@Test
	void findByIdWhenChangeSessionIdThenUsesSessionIdGenerationStrategy() {
		this.repository.setSessionIdGenerationStrategy(() -> "test");
		MapSession saved = new MapSession("original");
		saved.setAttribute("savedName", "savedValue");
		given(this.sessions.get(eq(saved.getId()))).willReturn(saved);

		HazelcastSession session = this.repository.findById(saved.getId());

		assertThat(session.getId()).isEqualTo(saved.getId());
		assertThat(session.changeSessionId()).isEqualTo("test");
	}

}
