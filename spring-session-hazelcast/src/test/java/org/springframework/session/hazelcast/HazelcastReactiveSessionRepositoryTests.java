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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import com.hazelcast.internal.serialization.impl.SerializationServiceV1;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.util.executor.CompletedFuture;
import org.junit.Before;
import org.junit.Test;

import org.springframework.session.MapSession;
import org.springframework.session.hazelcast.HazelcastReactiveSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
public class HazelcastReactiveSessionRepositoryTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);

	@SuppressWarnings("unchecked")
	private IMap<String, MapSession> sessions = mock(IMap.class);

	private HazelcastReactiveSessionRepository repository;

	@Before
	public void setUp() {
		ICompletableFuture mockFuture = mock(ICompletableFuture.class);
		given(sessions.setAsync(anyString(), any())).willReturn(mockFuture);
		given(sessions.setAsync(anyString(), any(), anyLong(), any())).willReturn(mockFuture);
		given(sessions.getAsync(anyString())).willReturn(mockFuture);
		given(sessions.submitToKey(anyString(), any())).willReturn(mockFuture);
		given(this.hazelcastInstance.<String, MapSession>getMap(anyString()))
				.willReturn(this.sessions);
		this.repository = new HazelcastReactiveSessionRepository(this.hazelcastInstance);
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

		HazelcastSession session = this.repository.createSession().block();

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

		HazelcastSession session = this.repository.createSession().block();

		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(Duration.ofSeconds(interval));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveNewFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession().block();
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveNewFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession().block();
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedAttributeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession().block();
		session.setAttribute("testName", "testValue");
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedAttributeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession().block();
		session.setAttribute("testName", "testValue");
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).submitToKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void removeAttributeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession().block();
		session.removeAttribute("testName");
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void removeAttributeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession().block();
		session.removeAttribute("testName");
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).submitToKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedLastAccessedTimeFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession().block();
		session.setLastAccessedTime(Instant.now());
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedLastAccessedTimeFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession().block();
		session.setLastAccessedTime(Instant.now());
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).submitToKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedMaxInactiveIntervalInSecondsFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession().block();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		verifyZeroInteractions(this.sessions);

		this.repository.save(session);
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUpdatedMaxInactiveIntervalInSecondsFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession().block();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		verify(this.sessions, times(2)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));
		verify(this.sessions, times(1)).submitToKey(eq(session.getId()),
				any(EntryProcessor.class));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUnchangedFlushModeOnSave() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		HazelcastSession session = this.repository.createSession().block();
		this.repository.save(session);
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveUnchangedFlushModeImmediate() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		this.repository.setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);

		HazelcastSession session = this.repository.createSession().block();
		verify(this.sessions, times(1)).setAsync(eq(session.getId()),
				eq(session.getDelegate()), isA(Long.class), eq(TimeUnit.SECONDS));

		this.repository.save(session);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionNotFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String sessionId = "testSessionId";

		HazelcastSession session = this.repository.findById(sessionId).block();

		assertThat(session).isNull();
		verify(this.sessions, times(1)).getAsync(eq(sessionId));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionExpired() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		MapSession expired = new MapSession();
		expired.setLastAccessedTime(Instant.now()
				.minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));
		given(this.sessions.getAsync(eq(expired.getId()))).willReturn(
			new CompletedFuture<>(SerializationServiceV1.builder().build(), expired, Executors.newSingleThreadExecutor()));

		HazelcastSession session = this.repository.findById(expired.getId()).block();

		assertThat(session).isNull();
		verify(this.sessions, times(1)).getAsync(eq(expired.getId()));
		verify(this.sessions, times(1)).removeAsync(eq(expired.getId()));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionFound() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		MapSession saved = new MapSession();
		saved.setAttribute("savedName", "savedValue");
		given(this.sessions.getAsync(eq(saved.getId()))).willReturn(
			new CompletedFuture<>(SerializationServiceV1.builder().build(), saved, Executors.newSingleThreadExecutor()));

		HazelcastSession session = this.repository.findById(saved.getId()).block();

		assertThat(session.getId()).isEqualTo(saved.getId());
		assertThat(session.<String>getAttribute("savedName")).isEqualTo("savedValue");
		verify(this.sessions, times(1)).getAsync(eq(saved.getId()));
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void delete() {
		verify(this.sessions, times(1)).addEntryListener(any(MapListener.class),
				anyBoolean());

		String sessionId = "testSessionId";

		this.repository.deleteById(sessionId).block();

		verify(this.sessions, times(1)).removeAsync(eq(sessionId));
		verifyZeroInteractions(this.sessions);
	}


	@Test // gh-1120
	public void getAttributeNamesAndRemove() {
		HazelcastSession session = this.repository.createSession().block();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

}
