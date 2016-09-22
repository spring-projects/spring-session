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

package org.springframework.session;

import java.util.Collections;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link SafeRetrievingSessionRepository}.
 *
 * @author Vedran Pavic
 */
@RunWith(MockitoJUnitRunner.class)
public class SafeRetrievingSessionRepositoryTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Mock
	private FindByIndexNameSessionRepository<ExpiringSession> delegate;

	@Test
	public void createWithNullDelegateFails() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Delegate must not be null");
		new SafeRetrievingSessionRepository<Session>(null,
				Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
	}

	@Test
	public void createWithNullIgnoredExceptionsFails() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Ignored exceptions must not be empty");
		new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate, null);
	}

	@Test
	public void createWithEmptyIgnoredExceptionsFails() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Ignored exceptions must not be empty");
		new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
				Collections.<Class<? extends RuntimeException>>emptySet());
	}

	@Test
	public void createSession() {
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		repository.createSession();
		verify(this.delegate, times(1)).createSession();
		verifyZeroInteractions(this.delegate);
	}

	@Test
	public void saveSession() {
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		repository.save(new MapSession());
		verify(this.delegate, times(1)).save(any(ExpiringSession.class));
		verifyZeroInteractions(this.delegate);
	}

	@Test
	public void getSession() {
		ExpiringSession session = mock(ExpiringSession.class);
		given(this.delegate.getSession(anyString())).willReturn(session);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		assertThat(repository.getSession(UUID.randomUUID().toString()))
				.isEqualTo(session);
		verify(this.delegate, times(1)).getSession(anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionThrowsIgnoredException() {
		given(this.delegate.getSession(anyString())).willThrow(RuntimeException.class);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		assertThat(repository.getSession(UUID.randomUUID().toString())).isNull();
		verify(this.delegate, times(1)).getSession(anyString());
		verify(this.delegate, times(1)).delete(anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionThrowsIgnoredExceptionWithDeletionDisabled() {
		given(this.delegate.getSession(anyString())).willThrow(RuntimeException.class);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		repository.setDeleteOnIgnoredException(false);
		assertThat(repository.getSession(UUID.randomUUID().toString())).isNull();
		verify(this.delegate, times(1)).getSession(anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionThrowsNotIgnoredException() {
		this.thrown.expect(RuntimeException.class);
		given(this.delegate.getSession(anyString())).willThrow(RuntimeException.class);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(IllegalStateException.class));
		repository.getSession(UUID.randomUUID().toString());
		verify(this.delegate, times(1)).getSession(anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	public void deleteSession() {
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		repository.delete(UUID.randomUUID().toString());
		verify(this.delegate, times(1)).delete(anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	public void findByIndexNameAndIndexValue() {
		ExpiringSession session = mock(ExpiringSession.class);
		given(this.delegate.findByIndexNameAndIndexValue(anyString(), anyString()))
				.willReturn(Collections.singletonMap("name", session));
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		assertThat(repository.findByIndexNameAndIndexValue("name", "value").get("name"))
				.isEqualTo(session);
		verify(this.delegate, times(1)).findByIndexNameAndIndexValue(anyString(),
				anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValueThrowsIgnoredException() {
		given(this.delegate.findByIndexNameAndIndexValue(anyString(), anyString()))
				.willThrow(RuntimeException.class);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(RuntimeException.class));
		assertThat(repository.findByIndexNameAndIndexValue("name", "value")).isEmpty();
		verify(this.delegate, times(1)).findByIndexNameAndIndexValue(anyString(),
				anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValueThrowsNotIgnoredException() {
		this.thrown.expect(RuntimeException.class);
		given(this.delegate.findByIndexNameAndIndexValue(anyString(), anyString()))
				.willThrow(RuntimeException.class);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(this.delegate,
						Collections.<Class<? extends RuntimeException>>singleton(IllegalStateException.class));
		repository.findByIndexNameAndIndexValue("name", "value");
		verify(this.delegate, times(1)).findByIndexNameAndIndexValue(anyString(),
				anyString());
		verifyZeroInteractions(this.delegate);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValueOnSessionRepositoryThrowsException() {
		this.thrown.expect(UnsupportedOperationException.class);
		SessionRepository delegate = mock(SessionRepository.class);
		SafeRetrievingSessionRepository<ExpiringSession> repository =
				new SafeRetrievingSessionRepository<ExpiringSession>(delegate,
						Collections.<Class<? extends RuntimeException>>singleton(IllegalStateException.class));
		repository.findByIndexNameAndIndexValue("name", "value");
		verifyZeroInteractions(delegate);
	}

}
