/*
 * Copyright 2014-2021 the original author or authors.
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
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.ExtendedMapEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionUpdateEntryProcessorTests {

	private SessionUpdateEntryProcessor processor;

	@BeforeEach
	void setUp() {
		this.processor = new SessionUpdateEntryProcessor();
	}

	@Test
	void shouldReturnFalseIfNoSessionExistsInHazelcastMapEntry() {
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, MapSession> mapEntry = mock(ExtendedMapEntry.class);

		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.FALSE);
	}

	@Test
	void shouldUpdateMaxInactiveIntervalOnSessionAndSetMapEntryValueWithNewTimeToLive() {
		Duration newMaxInactiveInterval = Duration.ofSeconds(123L);
		MapSession mapSession = new MapSession();
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, MapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		this.processor.setMaxInactiveInterval(newMaxInactiveInterval);
		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getMaxInactiveInterval()).isEqualTo(newMaxInactiveInterval);
		verify(mapEntry).setValue(mapSession, newMaxInactiveInterval.getSeconds(), TimeUnit.SECONDS);
	}

	@Test
	void shouldSetMapEntryValueWithOldTimeToLiveIfNoChangeToMaxInactiveIntervalIsRegistered() {
		Duration maxInactiveInterval = Duration.ofSeconds(123L);
		MapSession mapSession = new MapSession();
		mapSession.setMaxInactiveInterval(maxInactiveInterval);
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, MapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
		verify(mapEntry).setValue(mapSession, maxInactiveInterval.getSeconds(), TimeUnit.SECONDS);
	}

	@Test
	void shouldUpdateLastAccessTimeOnSessionAndSetMapEntryValueWithOldTimeToLive() {
		Instant lastAccessTime = Instant.ofEpochSecond(1234L);
		MapSession mapSession = new MapSession();
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, MapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		this.processor.setLastAccessedTime(lastAccessTime);
		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getLastAccessedTime()).isEqualTo(lastAccessTime);
		verify(mapEntry).setValue(mapSession, mapSession.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
	}

	@Test
	void shouldUpdateSessionAttributesFromDeltaAndSetMapEntryValueWithOldTimeToLive() {
		MapSession mapSession = new MapSession();
		mapSession.setAttribute("changed", "oldValue");
		mapSession.setAttribute("removed", "existingValue");
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, MapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		HashMap<String, Object> delta = new HashMap<>();
		delta.put("added", "addedValue");
		delta.put("changed", "newValue");
		delta.put("removed", null);
		this.processor.setDelta(delta);

		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat((String) mapSession.getAttribute("added")).isEqualTo("addedValue");
		assertThat((String) mapSession.getAttribute("changed")).isEqualTo("newValue");
		assertThat((String) mapSession.getAttribute("removed")).isNull();
		verify(mapEntry).setValue(mapSession, mapSession.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
	}

}
