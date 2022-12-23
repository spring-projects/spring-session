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
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.MapListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.hazelcast.ReactiveHazelcastSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;

/**
 * Tests for {@link ReactiveHazelcastSessionRepository}.
 *
 * @author Eleftheria Stein
 * @author Didier Loiseau
 */
class ReactiveHazelcastSessionRepositoryTests {

	private final HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);

	@SuppressWarnings("unchecked")
	private final IMap<String, MapSession> sessions = mock(IMap.class);

	private ReactiveHazelcastSessionRepository repository;

	@BeforeEach
	void setUp() {
		given(this.hazelcastInstance.<String, MapSession>getMap(anyString())).willReturn(this.sessions);
		this.repository = new ReactiveHazelcastSessionRepository(this.hazelcastInstance);
		this.repository.init();
		verify(this.sessions).addEntryListener(any(MapListener.class), anyBoolean());
	}

	@Test
	void constructorNullHazelcastInstance() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReactiveHazelcastSessionRepository(null))
				.withMessage("HazelcastInstance must not be null");
	}

	@Test
	void setSaveModeNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setSaveMode(null))
				.withMessage("saveMode must not be null");
	}

	@Test
	void createSessionDefaultMaxInactiveInterval() {
		HazelcastSession session = this.repository.createSession().block();

		// @formatter:off
		assertThat(session).isNotNull()
				.extracting(HazelcastSession::getMaxInactiveInterval)
				.isEqualTo(new MapSession().getMaxInactiveInterval());
		// @formatter:on
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void createSessionCustomMaxInactiveInterval() {
		int interval = 1;
		this.repository.setDefaultMaxInactiveInterval(interval);

		HazelcastSession session = this.repository.createSession().block();

		// @formatter:off
		assertThat(session).isNotNull()
				.extracting(HazelcastSession::getMaxInactiveInterval).isEqualTo(Duration.ofSeconds(interval));
		// @formatter:on
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveNewSession() {
		HazelcastSession session = createTestSession(true);
		CompletableFuture<Void> setFuture = new CompletableFuture<>();
		// @formatter:off
		given(this.sessions.setAsync(session.getId(), session.getDelegate(),
				MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS, TimeUnit.SECONDS))
				.willReturn(setFuture);
		// @formatter:on

		// @formatter:off
		this.repository.save(session)
				.as(StepVerifier::create)
				.expectSubscription()
				.expectNoEvent(Duration.ZERO)
				.then(() -> setFuture.complete(null))
				.verifyComplete();
		// @formatter:on
	}

	@Test
	void saveSessionIdChange() {
		HazelcastSession session = createTestSession(false);
		String oldSessionId = session.getId();
		session.changeSessionId();
		assertThat(session.getId()).isNotEqualTo(oldSessionId);

		CompletableFuture<MapSession> removeFuture = new CompletableFuture<>();
		given(this.sessions.removeAsync(oldSessionId)).willReturn(removeFuture);
		CompletableFuture<Void> setFuture = new CompletableFuture<>();
		// @formatter:off
		given(this.sessions.setAsync(session.getId(), session.getDelegate(),
				MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS, TimeUnit.SECONDS))
				.willReturn(setFuture);
		// @formatter:on

		// @formatter:off
		this.repository.save(session)
				.as(StepVerifier::create)
				.expectSubscription()
				.expectNoEvent(Duration.ZERO)
				.then(() -> {
					verify(this.sessions).removeAsync(oldSessionId);
					verifyNoMoreInteractions(this.sessions);

					removeFuture.complete(null);
					verify(this.sessions).setAsync(session.getId(), session.getDelegate(),
							MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
				})
				.expectNoEvent(Duration.ZERO)
				.then(() -> setFuture.complete(null))
				.verifyComplete();
		// @formatter:on
		verifyNoMoreInteractions(this.sessions);

		// a second save would be a no-op
		// @formatter:off
		this.repository.save(session)
				.as(StepVerifier::create)
				.verifyComplete();
		// @formatter:on
		verifyNoMoreInteractions(this.sessions);
	}

	@Test
	void saveUpdatedAttribute() {
		HazelcastSession session = createTestSession(false);
		session.setAttribute("testName", "testValue");
		assertThat(session.hasChanges()).isTrue();

		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(null, null,
				buildDelta("testName", "testValue"));
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	@Test
	void removeAttribute() {
		HazelcastSession session = createTestSession(false);
		session.removeAttribute("testName");
		assertThat(session.hasChanges()).isTrue();

		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(null, null, buildDelta("testName", null));
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	@Test
	void saveUpdatedLastAccessedTime() {
		HazelcastSession session = createTestSession(false);
		Instant accessedTime = Instant.now();
		session.setLastAccessedTime(accessedTime);
		assertThat(session.hasChanges()).isTrue();

		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(accessedTime, null, null);
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	@Test
	void saveUpdatedMaxInactiveIntervalInSeconds() {
		HazelcastSession session = createTestSession(false);
		Duration interval = Duration.ofSeconds(1);
		session.setMaxInactiveInterval(interval);
		assertThat(session.hasChanges()).isTrue();

		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(null, interval, null);
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	private void saveAndExpectUpdateUsingProcessor(HazelcastSession session,
			Hazelcast4SessionUpdateEntryProcessor processor) {
		CompletableFuture<Object> submitFuture = new CompletableFuture<>();
		given(this.sessions.submitToKey(session.getId(), processor)).willReturn(submitFuture);

		// @formatter:off
		this.repository.save(session)
				.as(StepVerifier::create)
				.expectSubscription()
				.expectNoEvent(Duration.ZERO)
				.then(() -> submitFuture.complete(Boolean.TRUE))
				.verifyComplete();
		// @formatter:on

		assertThat(session.hasChanges()).isFalse();
	}

	@Test
	void saveUnchanged() {
		HazelcastSession session = createTestSession(false);
		assertThat(session.hasChanges()).isFalse();
		// @formatter:off
		this.repository.save(session)
				.as(StepVerifier::create)
				.verifyComplete();
		// @formatter:on
		assertThat(session.hasChanges()).isFalse();
	}

	@Test
	void getSessionNotFound() {
		String sessionId = "testSessionId";
		given(this.sessions.getAsync(sessionId)).willReturn(CompletableFuture.completedFuture(null));

		// @formatter:off
		this.repository.findById(sessionId)
				.as(StepVerifier::create)
				.verifyComplete();
		// @formatter:on
	}

	@Test
	void getSessionExpired() {
		MapSession expired = new MapSession();
		expired.setLastAccessedTime(Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));
		given(this.sessions.getAsync(eq(expired.getId()))).willReturn(CompletableFuture.completedFuture(expired));
		CompletableFuture<MapSession> removeFuture = new CompletableFuture<>();
		given(this.sessions.removeAsync(expired.getId())).willReturn(removeFuture);

		// @formatter:off
		this.repository.findById(expired.getId())
				.as(StepVerifier::create)
				.expectSubscription()
				.expectNoEvent(Duration.ZERO)
				.then(() -> removeFuture.complete(null))
				.verifyComplete();
		// @formatter:on
	}

	@Test
	void getSessionFound() {
		MapSession saved = new MapSession();
		saved.setAttribute("savedName", "savedValue");
		CompletableFuture<MapSession> getFuture = new CompletableFuture<>();
		given(this.sessions.getAsync(eq(saved.getId()))).willReturn(getFuture);

		// @formatter:off
		this.repository.findById(saved.getId())
				.as(StepVerifier::create)
				.then(() -> getFuture.complete(saved))
				.expectNextMatches((hzSession) -> hzSession.getDelegate().equals(saved)
						&& hzSession.getId().equals(saved.getId())
						&& hzSession.getAttribute("savedName").equals("savedValue")
						&& !hzSession.hasChanges())
				.verifyComplete();
		// @formatter:on
	}

	@Test
	void delete() {
		String sessionId = "testSessionId";
		CompletableFuture<MapSession> removeFuture = new CompletableFuture<>();
		given(this.sessions.removeAsync(sessionId)).willReturn(removeFuture);

		// @formatter:off
		this.repository.deleteById(sessionId)
				.as(StepVerifier::create)
				.expectSubscription()
				.expectNoEvent(Duration.ZERO)
				.then(() -> removeFuture.complete(null))
				.verifyComplete();
		// @formatter:on
	}

	@Test // gh-1120
	void getAttributeNamesAndRemove() {
		HazelcastSession session = createTestSession(false);
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	void saveWithSaveModeOnSetAttribute() {
		this.repository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		HazelcastSession session = this.repository.new HazelcastSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");

		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(null, null,
				buildDelta("attribute3", "value4"));
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	@Test
	void saveWithSaveModeOnGetAttribute() {
		this.repository.setSaveMode(SaveMode.ON_GET_ATTRIBUTE);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		HazelcastSession session = this.repository.new HazelcastSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");

		HashMap<String, Object> delta = new HashMap<>();
		delta.put("attribute2", "value2");
		delta.put("attribute3", "value4");
		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(null, null, delta);
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	@Test
	void saveWithSaveModeAlways() {
		this.repository.setSaveMode(SaveMode.ALWAYS);
		MapSession delegate = new MapSession();
		delegate.setAttribute("attribute1", "value1");
		delegate.setAttribute("attribute2", "value2");
		delegate.setAttribute("attribute3", "value3");
		HazelcastSession session = this.repository.new HazelcastSession(delegate, false);
		session.getAttribute("attribute2");
		session.setAttribute("attribute3", "value4");

		HashMap<String, Object> delta = new HashMap<>();
		delta.put("attribute1", "value1");
		delta.put("attribute2", "value2");
		delta.put("attribute3", "value4");
		Hazelcast4SessionUpdateEntryProcessor processor = createProcessor(null, null, delta);
		saveAndExpectUpdateUsingProcessor(session, processor);
	}

	private HazelcastSession createTestSession(boolean isNew) {
		return this.repository.new HazelcastSession(new MapSession(), isNew);
	}

	private static Hazelcast4SessionUpdateEntryProcessor createProcessor(Instant accessedTime,
			Duration maxInactiveInterval, HashMap<String, Object> delta) {
		Hazelcast4SessionUpdateEntryProcessor processor = new Hazelcast4SessionUpdateEntryProcessor();
		processor.setLastAccessedTime(accessedTime);
		processor.setMaxInactiveInterval(maxInactiveInterval);
		processor.setDelta(delta);
		return processor;
	}

	private static HashMap<String, Object> buildDelta(String key, String value) {
		HashMap<String, Object> delta = new HashMap<>();
		delta.put(key, value);
		return delta;
	}

}
